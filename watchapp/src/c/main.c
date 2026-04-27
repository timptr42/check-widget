#include <pebble.h>
#include "message_keys.auto.h"

#define PERSIST_STATUS_LINE 1
#define MAX_STATUS_CHARS 32
#define MAX_STATUS_MARKERS 10

static Window *s_window;
static TextLayer *s_time_layer;
static TextLayer *s_battery_layer;
static TextLayer *s_bt_layer;
static Layer *s_status_layer;
static char s_time_text[8];
static char s_battery_text[8];
static char s_status_line[MAX_STATUS_CHARS] = "";
static BatteryChargeState s_battery_state;
static bool s_bt_connected;

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

static void update_time(void) {
  clock_copy_time_string(s_time_text, sizeof(s_time_text));
  text_layer_set_text(s_time_layer, s_time_text);
}

static void update_battery(BatteryChargeState state) {
  s_battery_state = state;
  snprintf(s_battery_text, sizeof(s_battery_text), "%d%%", state.charge_percent);
  text_layer_set_text(s_battery_layer, s_battery_text);
  text_layer_set_text_color(s_battery_layer, color_for_battery(state.charge_percent));
}

static void update_bt(bool connected) {
  s_bt_connected = connected;
  text_layer_set_text(s_bt_layer, "BT");
  text_layer_set_text_color(s_bt_layer, connected ? GColorGreen : GColorRed);
}

static void tick_handler(struct tm *tick_time, TimeUnits units_changed) {
  update_time();
}

static void battery_handler(BatteryChargeState state) {
  update_battery(state);
}

static void bt_handler(bool connected) {
  update_bt(connected);
}

static void status_layer_update(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  graphics_context_set_text_color(ctx, GColorWhite);

  int len = strlen(s_status_line);
  if (len == 0) {
    graphics_draw_text(ctx, "WAIT", fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD),
                       bounds, GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
    return;
  }

  int marker_width = 22;
  int marker_height = 24;
  int columns = bounds.size.w / marker_width;
  if (columns < 1) {
    columns = 1;
  }
  if (columns > MAX_STATUS_MARKERS) {
    columns = MAX_STATUS_MARKERS;
  }

  int rows = (len + columns - 1) / columns;
  int y = (bounds.size.h - rows * marker_height) / 2;
  if (y < 0) {
    y = 0;
  }

  for (int row = 0; row < rows; row++) {
    int row_start = row * columns;
    int row_count = len - row_start;
    if (row_count > columns) {
      row_count = columns;
    }
    int row_width = row_count * marker_width;
    int x = (bounds.size.w - row_width) / 2;
    if (x < 0) {
      x = 0;
    }

    for (int col = 0; col < row_count; col++) {
      char marker[4];
      marker[0] = '[';
      marker[1] = s_status_line[row_start + col];
      marker[2] = ']';
      marker[3] = '\0';

      graphics_context_set_text_color(ctx, color_for_status(marker[1]));
      graphics_draw_text(ctx, marker, fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),
                         GRect(x + col * marker_width, y + row * marker_height, marker_width, marker_height),
                         GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
    }
  }
}

static void inbox_received(DictionaryIterator *iter, void *context) {
  Tuple *statuses = dict_find(iter, MESSAGE_KEY_statuses);
  if (statuses && statuses->type == TUPLE_CSTRING) {
    strncpy(s_status_line, statuses->value->cstring, sizeof(s_status_line) - 1);
    s_status_line[sizeof(s_status_line) - 1] = '\0';
    persist_write_string(PERSIST_STATUS_LINE, s_status_line);
    layer_mark_dirty(s_status_layer);
  }
}

static void request_phone_update(void) {
  DictionaryIterator *out;
  AppMessageResult result = app_message_outbox_begin(&out);
  if (result != APP_MSG_OK || !out) {
    return;
  }
  dict_write_cstring(out, MESSAGE_KEY_request, "refresh");
  app_message_outbox_send();
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

  s_time_layer = create_text_layer(GRect(0, 12, bounds.size.w, 42),
                                   fonts_get_system_font(FONT_KEY_BITHAM_42_BOLD),
                                   GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_time_layer));

  s_battery_layer = create_text_layer(GRect(18, 58, 54, 24),
                                      fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD),
                                      GTextAlignmentLeft);
  layer_add_child(root, text_layer_get_layer(s_battery_layer));

  s_bt_layer = create_text_layer(GRect(bounds.size.w - 50, 58, 32, 24),
                                 fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD),
                                 GTextAlignmentRight);
  layer_add_child(root, text_layer_get_layer(s_bt_layer));

  s_status_layer = layer_create(GRect(0, 86, bounds.size.w, bounds.size.h - 90));
  layer_set_update_proc(s_status_layer, status_layer_update);
  layer_add_child(root, s_status_layer);

  if (persist_exists(PERSIST_STATUS_LINE)) {
    persist_read_string(PERSIST_STATUS_LINE, s_status_line, sizeof(s_status_line));
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
  app_message_open(256, 64);

  tick_timer_service_subscribe(MINUTE_UNIT, tick_handler);
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
