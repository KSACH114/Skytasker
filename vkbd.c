#include <linux/uhid.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <stdint.h>
#include <poll.h>

// 标准 USB 键盘的 HID 报告描述符
static const uint8_t hid_keyboard_desc[] = {
    0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,
    0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
    0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
    0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
    0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05,
    0x91, 0x02,
    0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
    0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07,
    0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
    0xC0
};

// 写入 uhid 事件
static int write_event(int fd, struct uhid_event *ev) {
    ssize_t n = write(fd, ev, sizeof(*ev));
    if (n < 0) {
        perror("write /dev/uhid");
        return -1;
    }
    return 0;
}

// 发送 HID 输入报告
static int send_hid_report(int fd, const uint8_t *data, uint16_t len) {
    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_INPUT2;
    ev.u.input2.size = len;
    memcpy(ev.u.input2.data, data, len);
    return write_event(fd, &ev);
}

int main(void) {
    int fd = open("/dev/uhid", O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        perror("open /dev/uhid");
        return 1;
    }

    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));

    // 创建虚拟键盘
    ev.type = UHID_CREATE2;
    strncpy((char *)ev.u.create2.name, "Virtual Keyboard",
            sizeof(ev.u.create2.name) - 1);
    strncpy((char *)ev.u.create2.phys, "uhid/vkbd",
            sizeof(ev.u.create2.phys) - 1);
    ev.u.create2.rd_size = sizeof(hid_keyboard_desc);
    memcpy(ev.u.create2.rd_data, hid_keyboard_desc, sizeof(hid_keyboard_desc));
    ev.u.create2.bus = BUS_USB;
    ev.u.create2.vendor = 0x1234;
    ev.u.create2.product = 0x5678;
    ev.u.create2.version = 1;
    ev.u.create2.country = 0;

    if (write_event(fd, &ev) < 0) return 1;

    // 等 UHID_START
    for (;;) {
        ssize_t n = read(fd, &ev, sizeof(ev));
        if (n < 0) {
            if (errno == EINTR) continue;
            perror("read /dev/uhid");
            return 1;
        }
        if (ev.type == UHID_START) break;
    }

    // 排空内核积压的 UHID_OUTPUT（LED 状态等），否则后续按键会被堵死
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN;
    while (poll(&pfd, 1, 0) > 0) {
        if (read(fd, &ev, sizeof(ev)) < 0) break;
    }

    // 先发一个空报告激活通道
    uint8_t empty_report[8] = {0};
    send_hid_report(fd, empty_report, sizeof(empty_report));
    usleep(50000);

    // 键盘已挂载。进入常驻循环：stdin 每收到一个字节就发一次 Shift，EOF 才销毁退出。
    // 键盘常驻可避免游戏每次「插入→移除」重新枚举输入设备导致卡顿。
    printf("READY\n"); fflush(stdout);

    uint8_t shift_down[8] = {0x02, 0, 0, 0, 0, 0, 0, 0};
    uint8_t shift_up[8]   = {0};

    char ch;
    while (read(STDIN_FILENO, &ch, 1) > 0) {
        printf("KEY\n"); fflush(stdout);
        send_hid_report(fd, shift_down, sizeof(shift_down));
        usleep(500000); // 按住 500ms
        send_hid_report(fd, shift_up, sizeof(shift_up));
        printf("KEY_DONE\n"); fflush(stdout);
    }

    // stdin EOF → 销毁虚拟键盘
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_DESTROY;
    write_event(fd, &ev);
    close(fd);
    return 0;
}
