# System76 Pangolin (pang14) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/pang14/specs/
来源 https://system76.com/tech-docs/models/pang14/external-overview/
来源 https://system76.com/tech-docs/models/pang14/internal-overview/
来源 https://system76.com/tech-docs/models/pang14/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Pangolin | pang14.webp]

The System76 Pangolin is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports AMD Ryzen (Phoenix) Mobile CPUs
    - [Ryzen 7 7840U](https://www.amd.com/en/products/apu/amd-ryzen-7-7840u)

来源章节：Specifications / CPU

## specs-bios：Specifications / BIOS

- Winbond W25R256JW flash chip
    - WSON-8 form factor
- Programmed with System76 firmware (non-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5571
    - Programmed with non-open EC firmware

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- GPU: AMD Radeon 780M
- eDP display: 16.1" 1920x1080@60Hz LCD
    - LCD panel: Innolux N161HCA-EA3 (or equivalent)
- External video output:
    - 1x HDMI 2.0
    - 1x DisplayPort 1.4 over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- 32GB (4x8GB) LPDDR5 (on-board) @ 6400 MHz
    - [Micron MT62F2G32D8DR-031](https://www.micron.com/products/memory/dram-components/lpddr5/part-catalog/part-detail/mt62f2g32d8dr-031-wt-b)
    - [SK Hynix H9JCNNNFA5MLYR-N6E](https://product.skhynix.com/products/dram/lpddr/lpddr5.go?appTypCd=APX02&treeNo=1054)
    - ...or other equivalent.

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- Gigabit Ethernet
- M.2 PCIe WiFi/Bluetooth
    - [Mediatek MT7921K](https://fcc.id/RAS-MT7921K)

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 90W (19V, 4.74A) DC-in port
    - Barrel size: 5.5mm (outer), 2.5mm (inner)
- Included AC adapter: ShenZhen SOY-1900474
    - AC power cord type: IEC C13
- USB-C charging compatible with 90W+ charger
- 70Wh 3-cell Lithium-Ion battery
    - Model number: 628467-3S1P

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- HDMI audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 2x M.2 (PCIe NVMe Gen 4)
- SD card reader

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB 3.2 Gen 2 Type-C
    - Supports DisplayPort over USB-C
    - Supports USB-C charging (USB-PD)
    - Does not support Thunderbolt
- 3x USB 3.2 Gen 1 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 1.80cm x 37.06cm x 24.79cm, 1.79kg

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

[Figure: Keyboard & Touchpad | overview-top.webp]

来源章节：External Overview / Keyboard & touchpad

## exterior-lid：External Overview / Lid

[Figure: Lid | overview-lid.webp]

来源章节：External Overview / Lid

## exterior-materials：External Overview / Materials

| Part                                 | Material        |
|--------------------------------------|-----------------|
| LCD back cover (lid)                 | Magnesium Alloy |
| LCD front cover (bezel)              | Plastic         |
| Top case (palm rests/port covers)    | Magnesium Alloy |
| Bottom panel                         | Magnesium Alloy |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

| Icon                                    | State    | Description                       |
|-----------------------------------------|----------|-----------------------------------|
| [Figure: Charging LED | led-charging.svg] | On       | Charging                          |
|                                         | Off      | Fully charged                     |
| [Figure: Webcam LED | led-webcam.svg ]    | On       | Webcam disabled (privacy mode on) |
|                                         | Off      | Webcam enabled (privacy mode off) |
| [Figure: Storage LED | led-power.svg]     | On       | Powered on                        |
|                                         | Pulsing  | Suspended                         |
|                                         | Off      | Powered off                       |

来源章节：External Overview / LED indicators

## exterior-keyboard-shortcuts：External Overview / Keyboard shortcuts

| Key                         | Shortcut| Action                     |
|-----------------------------|---------|----------------------------|
| [Figure: Fn-F1 | fn-f1.svg]   | Fn+F1   | Mute                       |
| [Figure: Fn-F2 | fn-f2.svg]   | Fn+F2   | Volume down                |
| [Figure: Fn-F3 | fn-f3.svg]   | Fn+F3   | Volume up                  |
| [Figure: Fn-F6 | fn-f6.svg]   | Fn+F6   | Toggle trackpad            |
| [Figure: Fn-F7 | fn-f7.svg]   | Fn+F7   | Cycle  keyboard brightness |
| [Figure: Fn-F9 | fn-f9.svg]   | Fn+F9   | Toggle airplane mode       |
| [Figure: Fn-F10 | fn-f10.svg] | Fn+F10  | Toggle displays            |
| [Figure: Fn-F11 | fn-f11.svg] | Fn+F11  | Screen brightness down     |
| [Figure: Fn-F12 | fn-f12.svg] | Fn+F12  | Screen brightness up       |

来源章节：External Overview / Keyboard shortcuts

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- M.2 SSDs are highlighted in dark green
- Wireless card is highlighted in dark blue
- LCD panel connector is highlighted in red*
- CMOS battery connector is highlighted in yellow
- Main battery connector is highlighted in purple
- Fan connectors are highlighted in orange
- BIOS flash chip (U4) is highlighted in black
- Speaker connector is highlighted in cyan
- Touchpad connector is highlighted in white*
- Keyboard connector is highlighted in light green*
- Keyboard backlight connector is highlighted in pink*

\* Marked connectors are protected by black tape by default.

来源章节：Internal Overview / Bottom components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Pangolin can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-replacing-an-m-2-nvme-ssd：Parts & Repairs / Replacing an M.2/NVMe SSD

This model supports two M.2 SSDs. Both slots support PCIe NVMe Generation 4 connections and M.2 size 2280.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 3 minutes
**Difficulty:** Easy ●

### Steps to replace the M.2 drive:

1. Unscrew the M.2 access door screw.

[Figure: M.2 access door | m2-access-door.webp]

2. Pull the M.2 access door off, starting from the top (larger side).
    - A flat, plastic tool can be used to pry the edge of the door up.
    - If the door is difficult to remove, the M.2 drives can alternatively be accessed by removing the bottom cover.
3. Unscrew the retainer screw opposite the M.2 slot.

[Figure: M.2 slots | m2-slots.webp]

4. Remove the existing M.2 drive by pulling it out of the slot.
5. Insert the new M.2 drive into the slot and hold it in place.
    - If a slot is being used for the first time, the spare thermal pad (which is included in the Pangolin's box) can be applied to the silver backplate before placing the drive down on top of it.
    [Figure: M.2 thermal pads | m2-thermal-pads.webp]
6. Replace the retainer screw.

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Part numbers:**
- The bottom panel's part number is `NM16ARB-D`.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 11 bottom panel screws.
    - The M.2 access door screw does not need to be removed.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Lift the bottom panel off, starting from the hinges in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-removing-the-battery：Parts & Repairs / Removing the battery

The battery provides primary power whenever the system is unplugged.

**Part numbers:**
- The battery's model number is `628467-3S1P`, and the original part number is `3ICP7/85/67`.
    - Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number.
    - You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to remove the battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the four battery screws, highlighted green below.

[Figure: Battery screws | battery.webp]

3. Unplug the battery connector (highlighted cyan above) and lift the battery out of the chassis.

来源章节：Parts & Repairs / Removing the battery

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your Pangolin's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Part numbers:**
- The standard wireless card is a Mediatek `MT7921K`.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 12 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the bottom cover.
2. Locate the wireless module. Remove any clear and/or black tape that is securing the wires and card.

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

**Part numbers:**
- The CMOS battery is a standard 3V EVE CR2032 battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover.
2. Unplug the white connector attaching the CMOS battery to the motherboard (highlighted green below.)

[Figure: CMOS battery | cmos-battery.webp]

3. If you are replacing the CMOS battery, carefully peel it away from the adhesive holding it to the chassis.
4. To clear the CMOS, disconnect the main battery, open the lid of the machine, and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
5. Re-connect the CMOS battery and the main battery, and replace the bottom panel.
6. Power up the machine. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Pangolin 14 has a fan and heatsink assembly to cool the CPU. The fans and heatsink are held together with adhesive; it is possible to replace the fans individually, but removing the entire assembly is recommended to perform service.

If the fan becomes noisy and cleaning it out doesn't fix the issue, you may need a new fan. [Contact support](https://support.system76.com/) to start a warranty claim or parts purchase.

Depending on your climate and the age of the machine, it may be necessary to apply new thermal paste between the CPU and the heatsink. Thermal paste helps facilitate effective heat transfer between the CPU and the cooling equipment.

**Part numbers:**
- The heatsink's part number is `01.WW7.NM16ACPD002`.
- The left fan's part number is `01.SS9.NM16ACPD000`, and its model number is `B7007ASHNF2101TN`.
- The right fan's part number is `01.SS9.NM16ACPD001`, and its model number is `B7007ASHSF2101TN`.
- The complete fan/heatsink assembly is also known as `NM16AC-AC928-PEAK`.

**Tools required:** Cross-head (Phillips) screwdriver, thermal paste
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the fan/heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Remove any black tape that may be covering the fan screws and wires.
3. Remove the six fan screws, highlighted green below.

[Figure: Thermal screws | thermal-system.webp]

4. Unplug the fan connectors, highlighted cyan above.
5. Unscrew the three heatsink screws, highlighted red above.
    - The heatsink screws are held captive and will not come out of the heatsink.
6. Remove the fan and heatsink assembly from the system, being careful not to bend the heatsink pipe. It may take some pressure to break the seal of the thermal paste.
    - The fans are held onto the heatsink with adhesive strips on both the top and bottom; if they come loose, place them back into position between the strips before reinstalling the fan/heatsink assembly.
7. Using a paper towel, remove the existing silver thermal paste. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.
    - The pink thermal pads do not need to be removed.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

8. After cleaning the CPU and heatsink, apply a small line of thermal paste directly onto the CPU chip.

[Figure: Thermal paste application | thermal-paste-application.webp]

9. Carefully replace the heatsink.
10. Tighten the three heatsink screws.
11. Replace the fan screws, stick the adhesive sheet back on, and plug in the fan connectors.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers. The speakers are a connected set, and must be replaced together.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the speakers:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the left speaker (on the right side when the machine is upside-down) from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Speaker wires and posts | speakers.webp]

3. Pull both speakers up and off of the plastic posts.
4. Remove any tape necessary to free the wire between the left and right speakers.
5. Slide the new speakers onto the plastic posts, run the wire between the speakers through the channels in the chassis, and connect the left speaker to the motherboard.

来源章节：Parts & Repairs / Replacing the speakers
