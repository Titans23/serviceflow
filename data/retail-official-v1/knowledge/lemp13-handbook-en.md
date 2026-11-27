# System76 Lemur Pro (lemp13 & lemp13-b) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/lemp13/specs/
来源 https://system76.com/tech-docs/models/lemp13/external-overview/
来源 https://system76.com/tech-docs/models/lemp13/internal-overview/
来源 https://system76.com/tech-docs/models/lemp13/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Lemur Pro | lemp13.webp]

The System76 Lemur Pro is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports Intel Core Ultra Series 1 (Meteor Lake) CPUs
    - [Core Ultra 7 155U](https://www.intel.com/content/www/us/en/products/sku/237327/intel-core-ultra-7-processor-155u-12m-cache-up-to-4-80-ghz/specifications.html)
    - [Core Ultra 5 125U](https://www.intel.com/content/www/us/en/products/sku/237330/intel-core-ultra-5-processor-125u-12m-cache-up-to-4-30-ghz/specifications.html)

来源章节：Specifications / CPU

## specs-bios：Specifications / BIOS

- [XMC XM25QU256C](https://www.xmcwh.com/en/site/product_con/224) flash chip
    - WSON-8 form factor
- Programmed with [System76 Open Firmware](https://github.com/system76/firmware-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5570E
- Programmed with [System76 EC](https://github.com/system76/ec)

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- GPU: Intel Iris Xe Graphics
    - GOP driver is recommended, VBT is provided
- eDP display: 14.0" 1920x1200@60Hz LCD
    - LCD panel options:
        - Tianma TM140VDHP21
            - Brightness: 400 nits (cd/m²)
            - sRGB color coverage: 100%
        - BOE NE140WUM-N6F
            - Brightness: 400 nits (cd/m²)
            - sRGB color coverage: 100%
        - ...or other equivalent
- External video outputs:
    - 1x HDMI 2.1
    - 1x DisplayPort 1.4 over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- After July 2024 (lemp13-b only):
    - Channel 0: 8-GB on-board DDR5
        - 4x Samsung M425R1GB4PB0-CWMOD (2GB each)
    - Channel 1: 8-GB/16-GB/32-GB/48-GB DDR5 SO-DIMM @ 5600 MHz
- Before July 2024 (lemp13-b or lemp13):
    - Channel 0: 8-GB on-board DDR5
        - 4x Samsung K4RAH165VB-BCQK (2GB each)
    - Channel 1: 8-GB/16-GB/32-GB/48-GB DDR5 SO-DIMM @ 4800 MHz

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- M.2 PCIe/CNVi WiFi/Bluetooth options:
    - Intel Wi-Fi 7 BE200
        - Bluetooth 5.4
    - Intel Wi-Fi 6E AX210/AX211
        - Bluetooth 5.3

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 20V, 3.25A (65W) DC-in
    - Power input via either USB-C port
- Included AC adapter: Chicony A20-065N3A
    - AC power cord type: IEC C5
- 73Wh 4-cell Lithium-Ion battery
    - Model number: L240BAT-4-73

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Intel Meteor Lake-P HD Audio Controller
    - Realtek ALC245 audio codec
- Internal speakers & microphone
- 3.5mm headphone/microphone combo jack
- HDMI, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 2x M.2 (PCIe NVMe Gen 4)
- MicroSD card reader (OZ711LV2)

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB Type-C with Thunderbolt 4
    - Supports DisplayPort over USB-C
    - Supports USB-C charging (USB-PD)
- 1x USB 3.2 Gen 2 Type-C
    - Supports USB-C charging (USB-PD)
- 1x USB 3.2 Gen 2 Type-A
- 1x USB 3.2 Gen 1 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 1.7cm x 31.2cm x 22.3cm, 0.998kg

来源章节：Specifications / Dimensions

## exterior-left-side：External Overview / Left side

[Figure: Left Ports | ports-left.webp]

来源章节：External Overview / Left side

## exterior-right-side：External Overview / Right side

[Figure: Right Ports | ports-right.webp]

来源章节：External Overview / Right side

## exterior-front-side：External Overview / Front side

[Figure: Front Ports | ports-front.webp]

来源章节：External Overview / Front side

## exterior-back-side：External Overview / Back side

[Figure: Back Ports | ports-back.webp]

来源章节：External Overview / Back side

## exterior-keyboard-touchpad：External Overview / Keyboard & touchpad

### Before May 2024 (lemp13):

[Figure: Keyboard & Touchpad | keyboard-touchpad-original.webp]

### After May 2024 (lemp13-b):

[Figure: Keyboard & Touchpad | keyboard-b.png]

来源章节：External Overview / Keyboard & touchpad

## exterior-lid：External Overview / Lid

[Figure: Lid | lid.webp]

来源章节：External Overview / Lid

## exterior-speakers：External Overview / Speakers

The machine has two bottom-firing speakers near the front, angled outward.

[Figure: Bottom Ports | ports-bottom.webp]

来源章节：External Overview / Speakers

## exterior-materials：External Overview / Materials

|Part                              |Material                 |Part Number    |
|----------------------------------|-------------------------|---------------|
|LCD back cover (lid)              |Magnesium-aluminum alloy |6-39-L2401-022 |
|LCD front cover (bezel)           |Plastic                  |6-39-L2401-012 |
|Top case (palm rests/port covers) |Magnesium-aluminum alloy |6-39-L2402-032 |
|Bottom panel                      |Magnesium-aluminum alloy |6-39-L2403-012 |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

The color and pattern for the power/charging status LED indicate:

- **Solid green:** Powered on and not suspended.
- **Flashing green:** Powered on and suspended.
- **Solid yellow:** Powered off and charging.
- **Off:** Powered off and not charging.

来源章节：External Overview / LED indicators

## exterior-keyboard-shortcuts：External Overview / Keyboard Shortcuts

The Lemur Pro has the following actions available using the Fn and Function keys:

|Key                           |Shortcut|Action                             |
|------------------------------|--------|-----------------------------------|
|[Figure: Fn-F1 | fn-f1.png]     |Fn+F1   |Toggle trackpad                    |
|[Figure: Fn-F2 | fn-f2.png]     |Fn+F2   |Mute/unmute microphone             |
|[Figure: Fn-F3 | fn-f3.png]     |Fn+F3   |Mute/unmute speakers               |
|[Figure: Fn-F4 | fn-f4.png]     |Fn+F4   |Cycle keyboard backlight brightness|
|[Figure: Fn-F5 | fn-f5.png]     |Fn+F5   |Volume down                        |
|[Figure: Fn-F6 | fn-f6.png]     |Fn+F6   |Volume up                          |
|[Figure: Fn-F7 | fn-f7.png]     |Fn+F7   |Toggle displays                    |
|[Figure: Fn-F8 | fn-f8.png]     |Fn+F8   |Screen brightness down             |
|[Figure: Fn-F9 | fn-f9.png]     |Fn+F9   |Screen brightness up               |
|[Figure: Fn-F10 | fn-f10.png]   |Fn+F10  |Toggle webcam                      |
|[Figure: Fn-F11 | fn-f11.png]   |Fn+F11  |Toggle airplane mode               |
|[Figure: Fn-F12 | fn-f12.png]   |Fn+F12  |Suspend                            |
|[Figure: Fn-accent | fn-dia.jpg]|Fn+`    |Play/Pause                         |
|1                             |Fn+1    |Toggle fan between max/automatic   |

来源章节：External Overview / Keyboard Shortcuts

## interior-components-and-connectors：Internal Overview / Components and connectors

Components can be replaced using the instructions under Parts & Repairs. Connectors can be reseated for troubleshooting purposes.

[Figure: Internal Components | components-highlighted.webp]

- RAM slot is highlighted in maroon
- M.2 SSDs are highlighted in light green
    - Both slots support PCIe NVMe Gen 4
- Wireless card is highlighted in dark green
- Webcam/microphone connector is highlighted in cyan
- LCD cable connector is highlighted in yellow
- Touchpad connector is highlighted in orange
- Keyboard connector (main) is highlighted in brown
- Keyboard backlight connector is highlighted in white
- Battery connector is highlighted in light red
- CMOS battery connector is highlighted in navy
- Speaker connectors are highlighted in pink
- BIOS flash chip (U55) is highlighted in black

来源章节：Internal Overview / Components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Lemur Pro can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Part numbers:**

- The bottom panel's part number is `6-39-L2403-012`.
- Replacement product model stickers can be obtained from [System76 support](https://support.system76.com).

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●
**Screws:** 12 total

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 12 screws from the bottom panel.

[Figure: Bottom case screws | bottom-screws.webp]

3. Lift the bottom cover off, starting from the hinges in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Lemur Pro 13 comes with 8GB of 4800 MHz RAM soldered onto the motherboard, which cannot be replaced. There is an additional RAM slot, which can support up to a 48GB stick for 56GB of RAM total. The additional stick is a standard DDR5 SO-DIMM running at 4800 MHz.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the RAM:

1. Follow the steps above to remove the bottom cover.
2. Press the small tabs on both sides of the RAM simultaneously. The RAM should spring up to an angle.

[Figure: RAM tabs | ram-tabs.webp]

3. Remove the RAM from the slot.
4. Insert the new RAM (or reseat the existing RAM) by placing it into the keyed slot and pressing down until it clicks into place.

来源章节：Parts & Repairs / Replacing the RAM

## repairs-replacing-an-m-2-nvme-ssd：Parts & Repairs / Replacing an M.2/NVMe SSD

This model supports two M.2 SSDs. Both slots are size 2280 and support PCIe NVMe Generation 4.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the M.2 drive:

1. Follow the steps above to remove the bottom cover.
2. Unscrew the retainer screw opposite the M.2 slot.
    - SSD-1 is between the battery and the speaker.
    - SSD-2 is next to the RAM slot.

[Figure: M.2 slots | m2-slots.webp]

3. Remove the existing M.2 drive by pulling it out of the slot.
4. Insert the new M.2 drive into the slot and hold it down.
5. Replace the retainer screw.

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your system's WiFi and Bluetooth are both handled by the same module. It connects with a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Part numbers:**

- The standard wireless card is an Intel `AX211NGW`.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the bottom cover.
2. Locate the wireless card (next to the CPU fan.) Remove any clear tape that is securing the wires.

[Figure: Wireless card | wireless-card.webp]

3. Gently remove the two antennas (highlighted red above) by pulling them up and away from the wireless card.
4. Remove the retaining screw opposite the M.2 slot, highlighted yellow above.
5. The wireless card will pop up at an angle. Remove the card from the M.2 slot.
6. Insert the new wireless card into the M.2 slot at an angle.
7. Replace the retaining screw.
8. Attach the two antennas by aligning the circular fittings and pressing onto the wireless card. The connectors will snap into place. _Use caution when attaching the connectors; the pins can bend, break, or snap._

来源章节：Parts & Repairs / Replacing the wireless card

## repairs-replacing-the-main-battery：Parts & Repairs / Replacing the main battery

The battery provides primary power whenever the system is unplugged.

**Part numbers:**

- The battery's model number is `L240BAT-4-73`, and the original part number is `6-87-L240S-74B01`.
    - Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number.
    - You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the main battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the four silver battery screws along the perimeter of the battery.

[Figure: Main battery | battery.webp]

3. Unplug the white connector that connects the battery to the motherboard.
4. Remove the and replace the battery.

来源章节：Parts & Repairs / Replacing the main battery

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the computer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Part numbers:**

- The CMOS battery is a standard 3V KTS CR2032W battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover.
2. Follow the steps above to remove the SSD from slot 1, if installed.
3. Remove any clear tape that is securing the battery, then pull the battery away from the adhesive holding it to the case.
    - If you are only clearing the CMOS and are not replacing the CMOS battery, removing the SSD and CMOS battery is optional, but will make it easier to unplug the CMOS battery connector.

[Figure: CMOS battery | cmos-battery.webp]

4. Unplug the white connector attaching the CMOS battery to the motherboard.
5. To clear the CMOS, disconnect the main battery, open the lid of the machine, and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
6. Re-connect the CMOS battery and the main battery, and replace the SSD and bottom panel.
7. Power up the machine. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

If the CPU fan becomes noisy and cleaning it out doesn't fix the issue, you may need a new CPU fan. Contact support to start a warranty claim or parts purchase.

Depending on your climate and the age of the machine, it may be necessary to apply new thermal paste between the CPU and the heatsink. Thermal paste helps facilitate effective heat transfer between the CPU and the cooling equipment. These instructions can also be used in the unlikely event your heatsink needs to be replaced.

**Part numbers:**

- The fan/heatsink assembly's part number is `6-31-L240N-102`.
- The fan is an FCN `FSAP DFS5L22H15B85P EP`.
    - The fan/heatsink is produced as a single assembly. Sourcing a replacement fan without the heatsink may not be possible.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the fans/heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the small connector plugging the fan into the motherboard, highlighted yellow below.
    - This can alternatively be done after the fan/heatsink is removed.

[Figure: Heatsink/fan screws | thermal-system.webp]

3. Remove the silver fan and heatsink screws in the order of the stamped numbers, starting with #1, then #2, and continuing until #5.
4. Remove the heatsink/fan from the system, being careful not to bend the heatsink pipe. It may take some pressure to break the seal of the thermal paste.
5. Using a paper towel, remove the existing thermal paste. You may also use a small amount of rubbing alcohol to remove excess or difficult-to-remove paste.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

6. After cleaning the CPU and heatsink, apply a small line of thermal paste directly onto the CPU.

[Figure: Thermal paste application | thermal-paste-application.webp]

7. Carefully replace the fan and heatsink.
8. Replace the fan and heatsink screws in the order of the stamped numbers, starting with #1, then #2, and continuing until #5.
9. Plug the fan back in.
    - When plugging the fan in, the black wire goes on the left (closest to the side of the machine.)

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be removed and replaced individually.

**Part numbers:**

- The left speaker's model number is `L240TU-L-BASS`, and its part number is `6-23-5L240-0L2`.
- The right speaker's model number is `L240TU-R-BASS`, and its part number is `6-23-5L240-0R2`.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the speakers:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the speaker wire from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Speaker wires and tabs | speakers.webp]

3. Pull the tops of the black rubber circles over the gray tabs holding them in place.
4. Lift the speaker up and off of the mounting posts.

[Figure: Speaker removal | speaker-removal.webp]

5. Slide the new speaker onto the mounting posts and connect it to the motherboard.

来源章节：Parts & Repairs / Replacing the speakers
