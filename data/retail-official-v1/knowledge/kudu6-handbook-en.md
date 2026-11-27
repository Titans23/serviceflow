# System76 Kudu (kudu6) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/kudu6/specs/
来源 https://system76.com/tech-docs/models/kudu6/external-overview/
来源 https://system76.com/tech-docs/models/kudu6/internal-overview/
来源 https://system76.com/tech-docs/models/kudu6/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Kudu | kudu6.webp]

The System76 Kudu is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports AMD Ryzen (Cezanne) Mobile CPUs
    - [Ryzen 9 5900HX](https://www.amd.com/en/products/apu/amd-ryzen-9-5900hx)

来源章节：Specifications / CPU

## specs-bios：Specifications / BIOS

- W74M12JWSSIQ flash chip
    - SOIC-8 form factor
- Programmed with System76 firmware (non-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5570E
- Programmed with non-open EC firmware

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- Dedicated GPU:
    - NVIDIA GeForce RTX 3060
        - TGP: 60W
- Integrated GPU:
    - AMD Radeon RX Vega 8
- eDP display options:
    - 15.6" 1920x1080@144Hz LCD
        - LCD panel: AUO B156HAN08.4 (or equivalent)
- External video output:
    - 1x HDMI 2.1
    - 1x Mini DisplayPort 1.4
    - 1x DisplayPort 1.4 over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- Up to 64GB (2x32GB) dual-channel DDR4 SO-DIMMs @ 3200 MHz

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- 2.5-Gigabit Ethernet
- M.2 PCIe/CNVi WiFi/Bluetooth
    - Intel Wi-Fi 6 AX200/AX201

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 230W (19.5V, 11.8A) DC-in port
    - Barrel size: 5.5mm (outer), 2.5mm (inner)
- Included AC adapter: Chicony A17-230P1A
    - AC power cord type: IEC C13
- 48.96Wh 4-cell Lithium-Ion battery
    - Model number: NH50BAT-4

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- Dedicated microphone 3.5mm jack
- HDMI, Mini DisplayPort, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 2x M.2 (PCIe NVMe Gen 3)

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB 3.2 Gen 2 Type-C
    - Supports DisplayPort over USB-C
    - Does not support USB-C charging (USB-PD) or Thunderbolt
- 1x USB 3.2 Gen 2 Type-A
- 1x USB 3.2 Gen 1 Type-A
- 1x USB 2.0 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 15": 36.1cm x 25.8cm x 2.9cm, 2.2kg

来源章节：Specifications / Dimensions

## exterior-left-side-overview：External Overview / Left side overview

[Figure: Left Ports | ports-left.webp]

来源章节：External Overview / Left side overview

## exterior-right-side-overview：External Overview / Right side overview

[Figure: Right Ports | ports-right.webp]

来源章节：External Overview / Right side overview

## exterior-front-side-overview：External Overview / Front side overview

[Figure: Front Ports | ports-front.webp]

来源章节：External Overview / Front side overview

## exterior-back-side-overview：External Overview / Back side overview

[Figure: Back Ports | ports-back.webp]

来源章节：External Overview / Back side overview

## exterior-materials：External Overview / Materials

|Part                              |Material       |
|----------------------------------|---------------|
|LCD back cover (lid)              |Aluminum       |
|LCD front cover (bezel)           |Plastic        |
|Top case (palm rests/port covers) |Plastic        |
|Bottom panel                      |Plastic        |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

|Icon                                    |Color          |Description                                      |
|----------------------------------------|---------------|-------------------------------------------------|
|[Figure: Power LED | led-power.png]       |Orange         |Powered off, DC power plugged in                 |
|                                        |Green          |Powered on                                       |
|                                        |Blinking green |Sleeping (suspended)                             |
|[Figure: Battery LED | led-battery.png]   |Orange         |Battery charging                                 |
|                                        |Green          |Battery fully charged                            |
|                                        |Blinking orange|Battery critically low                           |
|[Figure: Storage LED | led-storage.png]   |Green          |Storage drive activity                           |

来源章节：External Overview / LED indicators

## exterior-keyboard-shortcuts：External Overview / Keyboard shortcuts

|Key                        |Shortcut|Action                       |
|---------------------------|--------|-----------------------------|
|[Figure: Fn-F1 | fn-f1.png]  |Fn+F1   |Toggle trackpad              |
|[Figure: Fn-F2 | fn-f2.png]  |Fn+F2   |Toggle built-in LCD      |
|[Figure: Fn-F3 | fn-f3.png]  |Fn+F3   |Mute                         |
|[Figure: Fn-F5 | fn-f5.png]  |Fn+F5   |Volume down                  |
|[Figure: Fn-F6 | fn-f6.png]  |Fn+F6   |Volume up                    |
|[Figure: Fn-F7 | fn-f7.png]  |Fn+F7   |Toggle displays              |
|[Figure: Fn-F8 | fn-f8.png]  |Fn+F8   |Screen brightness down       |
|[Figure: Fn-F9 | fn-f9.png]  |Fn+F9   |Screen brightness up         |
|[Figure: Fn-F10 | fn-f10.png]|Fn+F10  |Toggle webcam                |
|[Figure: Fn-F11 | fn-f11.png]|Fn+F11  |Toggle airplane mode         |
|[Figure: Fn-F12 | fn-f12.png]|Fn+F12  |Suspend                      |
|[Figure: Fn-F12 | fn-dia.jpg]|Fn+`    |Play/Pause                   |
|[Figure: Fn-* | fn-star.png] |Fn+*    |Toggle keyboard backlight    |
|/                          |Fn+/    |Cycle keyboard color         |
|-                          |Fn+-    |Decrease keyboard brightness |
|+                          |Fn++    |Increase keyboard brightness |

来源章节：External Overview / Keyboard shortcuts

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- Webcam/microphone connector is highlighted in cyan
- LCD panel connector is highlighted in pink
- DC-in power connector is highlighted in red
- BIOS flash chip (U51) is highlighted in black
- Fan connectors are highlighted in orange
- RAM is highlighted in dotted green
- M.2 SSDs are highlighted in dotted yellow
- Wireless card is highlighted in blue
- Touchpad connector is highlighted in white
- Speaker connectors are highlighted in solid yellow
- CMOS battery is highlighted in solid green

来源章节：Internal Overview / Bottom components and connectors

## interior-under-keyboard-components-and-connectors：Internal Overview / Under-keyboard components and connectors

[Figure: Under-Keyboard Connectors | under-keyboard.webp]

- Keyboard connector is highlighted in yellow
- Keyboard backlight connector is highlighted in red
- Power button/LED connector is highlighted in cyan
- Front LED connector is highlighted in green

来源章节：Internal Overview / Under-keyboard components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Kudu can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-battery：Parts & Repairs / Removing the battery

The battery provides primary power whenever the system is unplugged.

The model number for the Kudu 6's battery is `NH50BAT-4`, and the original part number is `6-87-NH50S-42D01`. Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number. You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** None
**Time estimate:** 1 minute
**Difficulty:** Easy ●

### Steps to remove the battery:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Slide the locking slider (highlighted cyan below) into the unlocked position.

[Figure: Battery | battery.webp]

3. While holding the springing slider (highlighted red above) in the unlocked position, grasp the tab in the corner and lift the battery out of the machine.

来源章节：Parts & Repairs / Removing the battery

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (such as USB drives) are unplugged or removed from the system.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Follow the steps above to remove the battery.
2. Remove the 16 bottom panel screws.
    - The silver hinge screw (highlighted cyan below) is slightly shorter than the rest.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Pull the bottom panel off of the case, starting behind the battery slot.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Kudu 6 supports up to 64GB (2x32GB) of DDR4 SO-DIMMs running at 3200MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the RAM:

1. Follow the steps above to remove the battery and remove the bottom cover.
2. Press the small tabs on both sides of the RAM simultaneously. The RAM should spring up to an angle.

[Figure: RAM removal | ram-slots.webp]

3. Remove the RAM from the slot.
4. Insert the new RAM (or reseat the existing RAM) by placing it in the keyed slot and pressing down on the RAM until it clicks into place.

来源章节：Parts & Repairs / Replacing the RAM

## repairs-replacing-an-m-2-nvme-ssd：Parts & Repairs / Replacing an M.2/NVMe SSD

This model supports up to two M.2 SSDs. Both M.2 slots are size 2280, and both slots support PCIe NVMe Generation 3 only.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the M.2 drive:

1. Follow the steps above to remove the battery and remove the bottom cover.
2. Unscrew the retainer screw opposite the M.2 slot.

[Figure: M.2 slots | m2-slots.webp]

3. Remove the existing M.2 drive by pulling it out of the slot.
4. Insert the new M.2 drive into the slot and hold it in place.
5. Replace the retainer screw.

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your Kudu's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the battery and remove the bottom cover.
2. Locate the wireless module. Remove any clear tape that is securing the wires.

[Figure: Wireless card | wireless-card.webp]

3. Gently remove the two antennas (highlighted red above) by pulling them up and away from the wireless card.
4. Remove the retaining screw opposite the M.2 slot, highlighted yellow above.
5. The wireless card will pop up at an angle. Remove the card from the M.2 slot.
6. Insert the new wireless card into the M.2 slot at an angle.
7. Replace the retaining screw.
8. Attach the two antennas by aligning the circular fittings and pressing onto the wireless card. The connectors will snap into place. _Use caution when attaching the connectors; the pins can bend, break, or snap._

来源章节：Parts & Repairs / Replacing the wireless card

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the computer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 15 minutes
**Difficulty:** Medium ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the main battery and remove the bottom cover.
2. Unplug the small white connector that connects the CMOS battery to the motherboard.
    - If you are replacing the battery, remove any clear tape, then gently pull from the sides to pry it up from where it's stuck to the case.

[Figure: CMOS battery | cmos-battery.webp]

3. To clear the CMOS, open the lid of the machine and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
4. Re-connect the CMOS battery, replace the bottom panel, and replace the main battery.
5. Power up the Kudu. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Kudu 6 has a single heatsink for the CPU and GPU. One fan is attached to the heatsink, while the other fan is separate from the heatsink.

If the fans become noisy and cleaning them out doesn't fix the issue, you may need a new fan. Contact Support to start a warranty claim or parts purchase.

Depending on your climate and the age of the machine, it may be necessary to apply new thermal paste between the CPU/GPU and the heatsink. Thermal paste helps facilitate effective heat transfer between the CPU/GPU and the cooling equipment. These instructions can also be used in the unlikely event that your heatsink needs to be replaced.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the standalone fan:

1. Follow the steps above to remove the battery and remove the bottom cover.
2. Locate the standalone fan.
    - The standalone fan is located between the battery slot and the M.2 drives, next to the DC-in port.
3. If there is any clear tape on the fan securing the DC-in cable, remove it.
4. Remove the three screws attaching the standalone fan to the chassis.
    - Do not remove the smaller screws holding the fan's cover on.

[Figure: Standalone fan screws | fan-screws.webp]

5. Unplug the fan connector, highlighted cyan above.
6. Lift the fan out of the system.
    - The fan grill at the edge of the chassis is part of the heatsink, and will not lift out with the fan.

### Steps to replace the heatsink/thermal paste:

1. Follow the steps above to remove the standalone fan.
    - Removing the standalone fan is not physically required, but is recommended to ensure the heatsink is reinstalled properly.
2. Remove the heatsink and fan screws in the order of the stamped numbers, starting with #1, then #2, and continuing until you have removed #10.
    - The first four screws (#1 - #4) are held captive and will not pull away from the heat sink when loosened.

[Figure: Thermal screws | thermal-screws.webp]

3. Unplug the white fan connector from the motherboard (highlighted cyan above).
4. Remove the heatsink/fan from the case, being careful not to bend the heatsink pipes. It may take some pressure to break the seal of the thermal paste.
5. Using a paper towel, remove the existing thermal paste. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

6. After cleaning the CPU, GPU, and heatsink, apply a small line of thermal paste directly onto the CPU and GPU chips.

[Figure: Thermal paste application | thermal-paste-application.webp]

7. Carefully replace the heatsink.
8. Replace the silver heatsink screws, starting with #1, then #2, and so on until #6.
9. Replace the black fan screws.
10. Plug the two white fan connectors back into the motherboard.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-dc-in-jack：Parts & Repairs / Replacing the DC-in jack

If the DC-in jack (power port) is no longer making contact with the barrel charger, and replacing the charger doesn't fix the issue, the DC-in jack can be replaced.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the DC-in jack:

1. Follow the steps above to remove the battery and remove the bottom cover.
2. Remove any clear tape securing the internal DC-in cable.
3. Unscrew the two screws holding the DC-in connector onto the chassis, highlighted cyan below.

[Figure: DC-in power connector | power-connector.webp]

4. Unplug the internal DC-in cable from the motherboard.
5. Plug in the new cable, replace the screws, and (optionally) replace the tape.

来源章节：Parts & Repairs / Replacing the DC-in jack

## repairs-replacing-the-keyboard：Parts & Repairs / Replacing the keyboard

Keyboard replacement is simple and requires only a cross-head screwdriver.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Medium ●

### Steps to replace the keyboard:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 2 keyboard screws, indicated by the small keyboard icons.

[Figure: Keyboard screws | keyboard-screws.webp]

3. Open the lid slightly and place the machine on its side.
4. Push the screwdriver into the keyboard push point (highlighted red above) until the keyboard pops out.

[Figure: Keyboard push point | keyboard-push-point.webp]

5. Set the machine back down and raise the keyboard away from the chassis. The larger ribbon cable is for the keyboard, while the smaller ribbon cable is for the keyboard backlight.

[Figure: Keyboard ribbon connectors | keyboard-ribbons.webp]

6. Flip the black latches upwards to free the ribbon cables.
7. Pull the ribbon cables out of the connectors.
8. Remove the keyboard and replace it with the new one.
9. Carefully slide both ribbon cables into their connectors.
10. Flip the black latches back into place to secure the ribbon cables.
11. Place the keyboard back into position, starting with the tabs on the bottom edge.
12. Secure the rest of the keyboard by pressing down on each of its edges.
13. Turn the machine lid-side down again and replace the two keyboard screws.

来源章节：Parts & Repairs / Replacing the keyboard
