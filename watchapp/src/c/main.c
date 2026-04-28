#include <pebble.h>
#include "message_keys.auto.h"

#define PERSIST_STATUS_LINE 1
#define PERSIST_LABELS 2
#define PERSIST_HAS_PAYLOAD 3
#define MAX_STATUS_CHARS 32
#define MAX_LABELS_CHARS 512
#define MAX_LABEL_CHARS 40
#define REQUEST_RETRY_SECONDS 30
#define MESSAGE_KEY_labels 4
#define ALL_GREEN_TEXT "all test services available"

static Window *s_window;
static TextLayer *s_time_layer;
static TextLayer *s_battery_layer;
static TextLayer *s_bt_layer;
static Layer *s_status_layer;
static char s_time_text[8];
static char s_battery_text[8];
static char s_status_line[MAX_STATUS_CHARS] = "";
static char s_labels[MAX_LABELS_CHARS] = "";
static bool s_bt_connected;
static time_t s_last_request_at;
static bool s_phone_pending;
static bool s_has_payload;
static int s_selected_index;

static void request_phone_update(void);

static GColor color_for_status(char status) {
  if (status == 'V') {
    return GColorGreen;
  }
  if (status == '!') {
    return GColorYellow;
  }
  return GColorRed;
}

static GColor color_for_battery(int percent) {
  if (percent <= 20) {
    return GColorRed;
  }
  if (percent <= 50) {
    return GColorYellow;
  }
  return GColorGreen;
}

static int status_count(void) {
  return strlen(s_status_line);
}

static bool is_problem_status(char status) {
  return status == 'X' || status == '!';
}

static bool has_problem_status(void) {
  int count = status_count();
  for (int i = 0; i < count; i++) {
    if (is_problem_status(s_status_line[i])) {
      return true;
    }
  }
  return false;
}

static int next_problem_index(int from_index) {
  int count = status_count();
  if (count <= 0) {
    return 0;
  }
  for (int step = 1; step <= count; step++) {
    int candidate = (from_index + step) % count;
    if (is_problem_status(s_status_line[candidate])) {
      return candidate;
    }
  }
  return 0;
}

static void update_time(void) {
  time_t now = time(NULL);
  struct tm *tick_time = localtime(&now);
  strftime(s_time_text, sizeof(s_time_text), "%H %M", tick_time);
  text_layer_set_text(s_time_layer, s_time_text);
}

static void update_battery(BatteryChargeState state) {
  snprintf(s_battery_text, sizeof(s_battery_text), "%d%%", state.charge_percent);
  text_layer_set_text(s_battery_layer, s_battery_text);
  text_layer_set_text_color(s_battery_layer, color_for_battery(state.charge_percent));
}

static void update_bt(bool connected) {
  s_bt_connected = connected;
  text_layer_set_text(s_bt_layer, "BT");
  text_layer_set_text_color(s_bt_layer, connected ? GColorGreen : GColorRed);
}

static void label_at_index(int index, char *buffer, size_t buffer_size) {
  buffer[0] = '\0';
  if (buffer_size == 0 || index < 0) {
    return;
  }

  int current = 0;
  size_t out = 0;
  for (size_t i = 0; s_labels[i] != '\0'; i++) {
    char ch = s_labels[i];
    if (ch == '|') {
      if (current == index) {
        break;
      }
      current++;
      out = 0;
      buffer[0] = '\0';
      continue;
    }
    if (current == index && out + 1 < buffer_size) {
      buffer[out++] = ch;
      buffer[out] = '\0';
    }
  }
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
  int count = status_count();
  if (count > 0 && has_problem_status()) {
    s_selected_index = next_problem_index(s_selected_index);
    layer_mark_dirty(s_status_layer);
  } else if (s_bt_connected && time(NULL) - s_last_request_at >= REQUEST_RETRY_SECONDS) {
    request_phone_update();
  }
}

static void battery_handler(BatteryChargeState state) {
  update_battery(state);
}

static void bt_handler(bool connected) {
  update_bt(connected);
  if (connected) {
    request_phone_update();
  }
}

static void draw_empty_state(GContext *ctx, GRect bounds) {
  const char *text = s_bt_connected ? (s_phone_pending ? "PHONE?" : "WAIT") : "NO BT";
  graphics_context_set_text_color(ctx, s_bt_connected ? GColorYellow : GColorRed);
  graphics_draw_text(ctx, text, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD),
                     bounds, GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
}

static void status_layer_update(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  int count = status_count();
  if (count <= 0) {
    if (s_has_payload) {
      return;
    }
    draw_empty_state(ctx, bounds);
    return;
  }

  bool has_problems = has_problem_status();
  if (has_problems && (s_selected_index >= count || !is_problem_status(s_status_line[s_selected_index]))) {
    s_selected_index = next_problem_index(count - 1);
  }

  const int gap = 1;
  const int bar_height = 18;
  const int bar_y = bounds.size.h - bar_height - 2;
  const int full_width = bounds.size.w;
  int segment_width = (full_width - gap * (count - 1)) / count;
  if (segment_width < 2) {
    segment_width = 2;
  }

  char label[MAX_LABEL_CHARS];
  if (has_problems) {
    label_at_index(s_selected_index, label, sizeof(label));
    if (label[0] == '\0') {
      snprintf(label, sizeof(label), "[%d/%d]", s_selected_index + 1, count);
    }
  } else {
    snprintf(label, sizeof(label), "%s", ALL_GREEN_TEXT);
  }

  graphics_context_set_text_color(ctx, has_problems ? GColorWhite : GColorGreen);
  graphics_draw_text(ctx, label, fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),
                     GRect(2, 0, bounds.size.w - 4, bar_y - 2),
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);

  for (int i = 0; i < count; i++) {
    int x = i * (segment_width + gap);
    int width = (i == count - 1) ? full_width - x : segment_width;
    graphics_context_set_fill_color(ctx, color_for_status(s_status_line[i]));
    graphics_fill_rect(ctx, GRect(x, bar_y, width, bar_height), 0, GCornerNone);
  }

  if (has_problems) {
    int selected_x = s_selected_index * (segment_width + gap);
    int selected_width = (s_selected_index == count - 1) ? full_width - selected_x : segment_width;
    GRect frame = GRect(selected_x, bar_y, selected_width, bar_height);
    graphics_context_set_stroke_color(ctx, GColorBlack);
    graphics_context_set_stroke_width(ctx, 4);
    graphics_draw_rect(ctx, frame);
    graphics_context_set_stroke_color(ctx, GColorWhite);
    graphics_context_set_stroke_width(ctx, 1);
    graphics_draw_rect(ctx, grect_inset(frame, GEdgeInsets(2)));
  }
}

static void inbox_received(DictionaryIterator *iter, void *context) {
  Tuple *statuses = dict_find(iter, MESSAGE_KEY_statuses);
  Tuple *labels = dict_find(iter, MESSAGE_KEY_labels);

  if (statuses && statuses->type == TUPLE_CSTRING) {
    strncpy(s_status_line, statuses->value->cstring, sizeof(s_status_line) - 1);
    s_status_line[sizeof(s_status_line) - 1] = '\0';
    s_has_payload = true;
    persist_write_string(PERSIST_STATUS_LINE, s_status_line);
    persist_write_bool(PERSIST_HAS_PAYLOAD, true);
    if (status_count() > 0) {
      s_selected_index = has_problem_status() ? next_problem_index(status_count() - 1) : 0;
    }
  }

  if (labels && labels->type == TUPLE_CSTRING) {
    strncpy(s_labels, labels->value->cstring, sizeof(s_labels) - 1);
    s_labels[sizeof(s_labels) - 1] = '\0';
    persist_write_string(PERSIST_LABELS, s_labels);
  }

  if (statuses || labels) {
    s_phone_pending = false;
    layer_mark_dirty(s_status_layer);
  }
}

static void inbox_dropped(AppMessageResult reason, void *context) {
  s_phone_pending = true;
  layer_mark_dirty(s_status_layer);
}

static void outbox_sent(DictionaryIterator *iter, void *context) {
  s_phone_pending = true;
  layer_mark_dirty(s_status_layer);
}

static void outbox_failed(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  s_phone_pending = false;
  layer_mark_dirty(s_status_layer);
}

static void request_phone_update(void) {
  DictionaryIterator *out;
  AppMessageResult result = app_message_outbox_begin(&out);
  if (result != APP_MSG_OK || !out) {
    return;
  }
  dict_write_cstring(out, MESSAGE_KEY_request, "refresh");
  s_last_request_at = time(NULL);
  s_phone_pending = true;
  app_message_outbox_send();
  layer_mark_dirty(s_status_layer);
}

static TextLayer *create_text_layer(GRect frame, GFont font, GTextAlignment alignment) {
  TextLayer *layer = text_layer_create(frame);
  text_layer_set_background_color(layer, GColorBlack);
  text_layer_set_text_color(layer, GColorWhite);
  text_layer_set_font(layer, font);
  text_layer_set_text_alignment(layer, alignment);
  return layer;
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  GRect bounds = layer_get_bounds(root);

  s_time_layer = create_text_layer(GRect(0, 10, bounds.size.w, 46),
                                   fonts_get_system_font(FONT_KEY_BITHAM_42_BOLD),
                                   GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_time_layer));

  s_battery_layer = create_text_layer(GRect(12, 58, 56, 22),
                                      fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),
                                      GTextAlignmentLeft);
  layer_add_child(root, text_layer_get_layer(s_battery_layer));

  s_bt_layer = create_text_layer(GRect(bounds.size.w - 44, 58, 32, 22),
                                 fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),
                                 GTextAlignmentRight);
  layer_add_child(root, text_layer_get_layer(s_bt_layer));

  s_status_layer = layer_create(GRect(0, 82, bounds.size.w, bounds.size.h - 84));
  layer_set_update_proc(s_status_layer, status_layer_update);
  layer_add_child(root, s_status_layer);

  if (persist_exists(PERSIST_STATUS_LINE)) {
    persist_read_string(PERSIST_STATUS_LINE, s_status_line, sizeof(s_status_line));
  }
  if (persist_exists(PERSIST_LABELS)) {
    persist_read_string(PERSIST_LABELS, s_labels, sizeof(s_labels));
  }
  if (persist_exists(PERSIST_HAS_PAYLOAD)) {
    s_has_payload = persist_read_bool(PERSIST_HAS_PAYLOAD);
  }

  update_time();
  update_battery(battery_state_service_peek());
  update_bt(connection_service_peek_pebble_app_connection());
  request_phone_update();
}

static void window_unload(Window *window) {
  text_layer_destroy(s_time_layer);
  text_layer_destroy(s_battery_layer);
  text_layer_destroy(s_bt_layer);
  layer_destroy(s_status_layer);
}

static void init(void) {
  s_window = window_create();
  window_set_background_color(s_window, GColorBlack);
  window_set_window_handlers(s_window, (WindowHandlers) {
    .load = window_load,
    .unload = window_unload
  });

  app_message_register_inbox_received(inbox_received);
  app_message_register_inbox_dropped(inbox_dropped);
  app_message_register_outbox_sent(outbox_sent);
  app_message_register_outbox_failed(outbox_failed);
  app_message_open(1024, 64);

  tick_timer_service_subscribe(SECOND_UNIT, tick_handler);
  battery_state_service_subscribe(battery_handler);
  connection_service_subscribe((ConnectionHandlers) {
    .pebble_app_connection_handler = bt_handler
  });

  window_stack_push(s_window, true);
}

static void deinit(void) {
  app_message_deregister_callbacks();
  tick_timer_service_unsubscribe();
  battery_state_service_unsubscribe();
  connection_service_unsubscribe();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
