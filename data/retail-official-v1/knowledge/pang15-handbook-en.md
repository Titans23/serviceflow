# System76 Pangolin (pang15) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/pang15/specs/
来源 https://system76.com/tech-docs/models/pang15/external-overview/
来源 https://system76.com/tech-docs/models/pang15/internal-overview/
来源 https://system76.com/tech-docs/models/pang15/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Pangolin | pang15.webp]

The System76 Pangolin is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports AMD Ryzen (Hawk Point) Mobile CPUs
    - [Ryzen 9 8945HS](https://www.amd.com/en/products/processors/laptop/ryzen/8000-series/amd-ryzen-9-8945hs.html)

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
- eDP display: 16.1" 2560×1600@120Hz LCD
    - LCD panel: BOE NE160QDM-NY2 (or equivalent)
        - Brightness: 350 nits (cd/m²)
        - sRGB color coverage: 100%
- External video output:
    - 1x HDMI 2.0
    - 2x DisplayPort 1.4 over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- Up to 96GB (2x48GB) dual-channel DDR5 @ 5600 MHz

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- WiFi 6E/Bluetooth 5.2 (soldered)
    - Model: AzureWave AW-XM514NF
    - Chipset: MediaTek MT7922A12L

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 100W USB-C charging
    - Lower-powered chargers supported with performance throttling
- Included USB-C charger: Dongguan Aohai A879-200500C-US1
- 100W (20V, 5A) DC-in port
    - Barrel size:
        - Outer port diameter: 3.5mm
        - Inner pin diameter: 1.35mm
        - Barrel length: 10mm
    - Barrel charger not included
- 57.75Wh 4-cell Lithium-Ion battery
    - Model number: U716849PHV-4S1P

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- HDMI & USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 2x M.2 (PCIe NVMe Gen 4)
- MicroSD card reader

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB 4.0 Type-C
    - Supports DisplayPort over USB-C
    - Supports USB-C charging (USB-PD)
    - Does not support Thunderbolt
- 1x USB 3.2 Gen 2 Type-C
    - Supports DisplayPort over USB-C
    - Does not support USB-C charging (USB-PD)
    - Does not support Thunderbolt
- 3x USB 3.2 Gen 1 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 1.8cm x 35.6cm x 24.6cm, 1.73kg

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

| Part                                 | Material | Part Number        |
|--------------------------------------|----------|--------------------|
| LCD back cover (lid)                 | Aluminum | 02.SKD.NM16AHFTX00 |
| LCD front cover (bezel)              | Plastic  | 02.SKD.NM16AHFTX00 |
| Top case (palm rests/port covers)    | Aluminum | 02.SKD.NM16AHFTX01 |
| Bottom panel                         | Aluminum | 02.SKD.NM16AHFTX02 |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

| Icon                                    | State    | Description                       |
|-----------------------------------------|----------|-----------------------------------|
| [Figure: Charging LED | led-charging.svg] | Green    | Fully charged                     |
|                                         | Red      | Charging                          |
|                                         | Off      | Not charging (disconnected)       |
| [Figure: Webcam LED | led-webcam.svg]     | On       | Webcam disabled (privacy mode on) |
|                                         | Off      | Webcam enabled (privacy mode off) |

来源章节：External Overview / LED indicators

## exterior-keyboard-shortcuts：External Overview / Keyboard shortcuts

| Key                         | Shortcut| Action                     |
|-----------------------------|---------|----------------------------|
| [Figure: Fn-F1 | fn-f1.svg]   | Fn+F1   | Mute                       |
| [Figure: Fn-F2 | fn-f2.svg]   | Fn+F2   | Volume down                |
| [Figure: Fn-F3 | fn-f3.svg]   | Fn+F3   | Volume up                  |
| [Figure: Fn-F4 | fn-f4.svg]   | Fn+F4   | Mute/unmute microphone     |
| [Figure: Fn-F6 | fn-f6.svg]   | Fn+F6   | Toggle trackpad            |
| [Figure: Fn-F7 | fn-f7.svg]   | Fn+F7   | Cycle keyboard brightness  |
| [Figure: Fn-F9 | fn-f9.svg]   | Fn+F9   | Toggle airplane mode       |
| [Figure: Fn-F10 | fn-f10.svg] | Fn+F10  | Toggle displays            |
| [Figure: Fn-F11 | fn-f11.svg] | Fn+F11  | Screen brightness down     |
| [Figure: Fn-F12 | fn-f12.svg] | Fn+F12  | Screen brightness up       |

来源章节：External Overview / Keyboard shortcuts

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- RAM slots are highlighted in maroon
    - RAM sticks are covered with protective plastic
- M.2 SSDs are highlighted in dark green
- Wireless card is highlighted in dark blue
- LCD panel connector is highlighted in red
    - Covered by black protective tape
- CMOS battery connector is highlighted in yellow
- Main battery connector is highlighted in purple
- Fan connectors are highlighted in orange
- Speaker connectors are highlighted in cyan
    - Left speaker connector (on the right side) is covered by black protective tape
- Keyboard connector is highlighted in light green
- Keyboard backlight connector is highlighted in pink
    - Covered by black protective tape
- Touchpad connector is highlighted in white
    - Covered by black protective tape
- BIOS flash chip is highlighted in black
    - Covered by the CPU heatsink pipe

来源章节：Internal Overview / Bottom components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Pangolin can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Part numbers:**
- The bottom panel's part number is `02.SKD.NM16AHFTX02`.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 7 bottom panel screws.
    - The back-center screw (highlighted red below) is longer than the rest of the screws.
    - The front four screws are mounted at an angle into the machine; hold the screwdriver at an angle for the easiest removal/installation.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Lift the bottom panel off, starting from the hinges in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Pangolin 15 supports up to 96GB (2x48GB) of DDR5 SO-DIMMs running at 5600MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the RAM:

1. Follow the steps above to remove the bottom cover.
2. Remove the black protective covers from the RAM slots, shown on the left below.
    - Alternatively, you can pull the outer ends of the covers up and hold them away from the RAM sticks while replacing them instead of entirely removing the covers from the slots.

[Figure: RAM slots | ram-slots.webp]

3. Press the small tabs on both sides of the RAM simultaneously. The RAM should spring up to an angle.
4. Remove the RAM from the slot.
5. Insert the new RAM (or reseat the existing RAM) by placing it into the keyed slot and pressing down until it clicks into place.

来源章节：Parts & Repairs / Replacing the RAM

## repairs-replacing-an-m-2-nvme-ssd：Parts & Repairs / Replacing an M.2/NVMe SSD

This model supports two M.2 SSDs. Both slots support PCIe NVMe Generation 4 connections and M.2 size 2280.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 3 minutes
**Difficulty:** Easy ●

### Steps to replace the M.2 drive:

1. Follow the steps above to remove the bottom cover.
2. Unscrew the retainer screw opposite the M.2 slot.

[Figure: M.2 slots | m2-slots.webp]

3. Remove the existing M.2 drive by pulling it out of the slot.
4. If replacing an M.2 drive, slowly peel the light blue thermal pad off of the old drive, then place it on the new drive.
    - If you ordered less than two drives when purchasing your system, the additional thermal pad will be included in the box.
    - M.2 thermal pads are optional, and drives can be used without thermal pads if the pads are lost or unable to be transferred. Without the thermal pad, performance may be throttled to avoid overheating.
5. Insert the new M.2 drive into the slot and hold it in place.
6. Replace the retainer screw.

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-replacing-the-battery：Parts & Repairs / Replacing the battery

The battery provides primary power whenever the system is unplugged.

**Part numbers:**
- The battery's model number is `U716849PHV-4S1P`, and the original part number is `4ICP8/68/49`.
    - Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number.
    - You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the seven battery screws, highlighted cyan below.

[Figure: Battery screws | battery.webp]

3. Unplug the battery connector (highlighted green above) and lift the battery out of the chassis.
    - Wires may be routed through the hooks on the left side of the battery. Free the wires, and push them back into the hooks when installing the battery again.
    - When plugging in the battery, the red wires go on the left, and the black wires go on the right.

来源章节：Parts & Repairs / Replacing the battery

## repairs-reseating-the-wireless-antenna-connectors：Parts & Repairs / Reseating the wireless antenna connectors

Your Pangolin's WiFi and Bluetooth are both handled by the same module, which is soldered to the Pangolin motherboard. If you are experiencing wireless connectivity issues and would like to reseat the wireless antenna connectors, you can do so using the instructions below.

**Part numbers:**
- The built-in wireless card is an AzureWave `AW-XM514NF`.
    - The wireless card chipset is a MediaTek `MT7922A12L`.
- The motherboard's model number is `EM_APX938_V2.0`.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 12 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the bottom cover.
2. Locate the wireless module. Remove the clear cover securing the wires and card (shown on the left below).

[Figure: Wireless card | wireless-card.webp]

3. Gently remove the two antennas (highlighted red above) by pulling them up and away from the wireless card.
4. Re-attach the two antennas by aligning the circular fittings and pressing onto the wireless card. The connectors will snap into place. _Use caution when attaching the connectors; the pins can bend, break, or snap._

来源章节：Parts & Repairs / Reseating the wireless antenna connectors

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the computer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Part numbers:**
- The CMOS battery is a standard 3V CR2032 battery. The stock battery is Malak brand.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover.
2. Unplug the white connector attaching the CMOS battery to the motherboard (highlighted cyan below.)

[Figure: CMOS battery | cmos-battery.webp]

3. If you are replacing the CMOS battery, carefully peel it away from the adhesive holding it to the chassis.
4. To clear the CMOS, disconnect the main battery, open the lid of the machine, and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
5. Re-connect the CMOS battery and the main battery, and replace the bottom panel.
    - When connecting the CMOS battery, the red wire goes on the left, and the black wire goes on the right.
6. Power up the machine. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Pangolin 15 has a fan and heatsink assembly to cool the CPU. The fans and heatsink are held together with adhesive; it is possible to replace the fans individually, but removing the entire assembly is recommended to perform service.

If the fan becomes noisy and cleaning it out doesn't fix the issue, you may need a new fan. [Contact support](https://support.system76.com/) to start a warranty claim or parts purchase.

Depending on your climate and the age of the machine, it may be necessary to apply new thermal paste between the CPU and the heatsink. Thermal paste helps facilitate effective heat transfer between the CPU and the cooling equipment.

**Part numbers:**
- The heatsink's part number is `01.WW7.APX938ARM00`.
- The left fan is a Joray `BF070E12P (S-0592)`, and its part number is `01.SS9.APX938DTH00`.
- The right fan is a Joray `BF080D12P (S-0593)`, and its part number is `01.SS9.APX938DTH01`.

**Tools required:** Cross-head (Phillips) screwdriver, thermal paste
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the fan/heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Slowly peel off the black coverings and their connecting strip from the heatsink, highlighted green below.

[Figure: Thermal system covers | thermal-system-covers.webp]

3. Remove the six fan screws, highlighted green below.

[Figure: Thermal screws | thermal-system.webp]

4. Unplug the fan connectors, highlighted cyan above.
5. Unscrew the four heatsink screws, highlighted red above.
    - The heatsink screws are held captive and will not come out of the heatsink.
6. Remove the fan and heatsink assembly from the system, being careful not to bend the heatsink pipe. It may take some pressure to break the seal of the thermal paste.
    - The fans are held onto the heatsink with adhesive strips on both the top and bottom; if they come loose, place them back into position between the strips before reinstalling the fan/heatsink assembly.
7. Using a paper towel, remove the existing silver thermal paste. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.
    - The pink thermal pads do not need to be removed.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

8. After cleaning the CPU and heatsink, apply a small line of thermal paste directly onto the CPU chip.

[Figure: Thermal paste application | thermal-paste-application.webp]

9. Carefully replace the heatsink.
10. Tighten the four heatsink screws.
11. Replace the fan screws, plug in the fan connectors, stick the black coverings back onto the heatsink pipes, and replace the bottom panel.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has four speakers (two top-firing tweeters and two bottom-firing woofers). The left and right channels can be replaced independently.

**Part numbers:**
- Left speakers (right side of machine when viewing from below):
    - Tweeter: `GS-NM16TG-L`
    - Woofer: `GS-NM16TG-L-2`
- Right speakers (left side of machine when viewing from below):
    - Tweeter: `GS-NM16TG-R`
    - Woofer: `GS-NM16TG-R-2`

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the right speakers:

1. Follow the steps above to remove the bottom cover.
2. Unplug the `SPK1` connector, highlighted cyan below.
    - Peel back any black tape covering the wires stemming from this connector.
    - This can alternatively be done after freeing the speakers.

[Figure: Right speakers and wire | speakers-right.webp]

3. Unscrew the right tweeter screws, highlighted pink above.
4. Pull the right woofer off of the plastic mounting posts (highlighted yellow above), then lift the speakers out of the machine.
    - The woofer wire is not routed underneath the battery by default, but may become routed underneath the battery after the battery is replaced. If this is the case, it may be necessary to remove the battery before removing the right woofer.
5. Slide the new woofer onto the plastic posts, screw the new tweeter in, and connect the `SPK1` connector.
6. Replace the black tape and bottom panel.

### Steps to replace the left speakers:

1. Follow the steps above to remove the bottom cover.
2. Remove the black tape (highlighted green below) covering the `SPK2` connector and the left tweeter.

[Figure: Left speaker tape | speakers-left-tape.webp]

3. Disconnect the `SPK2` connector from the motherboard, highlighted cyan below.
    - This can alternatively be done after freeing the speakers.

[Figure: Left speakers and wire | speakers-left.webp]

4. Unscrew the left tweeter screws, highlighted pink above.
5. Pull the left woofer off of the plastic mounting posts (highlighted yellow above), then lift the speakers out of the machine.
6. Slide the new woofer onto the plastic posts, screw the new tweeter in, and connect the `SPK2` connector.
7. Replace the black tape and bottom panel.

来源章节：Parts & Repairs / Replacing the speakers
