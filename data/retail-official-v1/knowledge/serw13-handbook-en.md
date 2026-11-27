# System76 Serval WS (serw13) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/serw13/specs/
来源 https://system76.com/tech-docs/models/serw13/external-overview/
来源 https://system76.com/tech-docs/models/serw13/internal-overview/
来源 https://system76.com/tech-docs/models/serw13/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Serval WS | serw13.webp]

The System76 Serval WS is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports Intel 13th & 14th Generation (Raptor Lake) CPUs
    - [Core i9-14900HX](https://ark.intel.com/content/www/us/en/ark/products/235995/intel-core-i9-processor-14900hx-36m-cache-up-to-5-80-ghz.html)
    - [Core i9-13900HX](https://ark.intel.com/content/www/us/en/ark/products/232171/intel-core-i913900hx-processor-36m-cache-up-to-5-40-ghz.html)

来源章节：Specifications / CPU

## specs-chipset：Specifications / Chipset

- [Intel HM770](https://www.intel.com/content/www/us/en/products/sku/232478/intel-hm770-chipset/specifications.html)

来源章节：Specifications / Chipset

## specs-bios：Specifications / BIOS

- GigaDevice GD25B256EYIGR
    - WSON-8 form factor
- Programmed with [System76 Open Firmware](https://github.com/system76/firmware-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5570E
- Programmed with [System76 EC](https://github.com/system76/ec)

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- GPU options:
    - NVIDIA GeForce RTX 4070
    - NVIDIA GeForce RTX 4060
- eDP display:
    - 17.3" 3840x2160@144Hz
        - LCD panel: AUO B173ZAN03.0 (or equivalent)
    - 17.3" 2560x1440@240Hz LCD
        - LCD panel: BOE NE173QHM-NZ1 (or equivalent)
    - 15.6" 1920x1080@165Hz LCD
        - LCD panel: BOE NV156FHM-NY8 (or equivalent)
- External video outputs:
    - 1x HDMI 2.1a
    - 1x Mini DisplayPort 1.4
    - 1x DisplayPort 1.4 over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- Up to 96GB (2x48GB) dual-channel DDR5 SO-DIMMs @ 5600 MHz

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- 2.5-Gigabit Ethernet
- M.2 PCIe/CNVi WiFi/Bluetooth options:
    - Intel Wi-Fi 7 BE200
        - Bluetooth 5.4
    - Intel Wi-Fi 6E AX210/AX211
        - Bluetooth 5.3

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 280W (20V, 14A) DC-in port
    - DC-in port accepts rectangular (not barrel) connector
- Included AC adapter: Chicony A18-280P1A
    - AC power cord type: IEC C13
- 80Wh 6-cell Lithium-Ion battery
    - 15" model number: PD50BAT-6-80
    - 17" model number: PD70BAT-6-80

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- Combined microphone & S/PDIF 3.5mm jack
- HDMI, Mini DisplayPort, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 2x PCIe NVMe Gen 4
- MicroSD card reader

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB Type-C with Thunderbolt 4
    - Supports DisplayPort over USB-C.
    - Supports USB-PD (charging) up to 100W when the system is powered off or suspended.
- 1x USB 3.2 Gen 2 Type-C
    - Partially supports DisplayPort over USB-C.
        - Display output is unreliable with some display brands;  usage of the Thunderbolt port is recommended instead.
- 2x USB 3.2 Gen 1 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 15": 2.49cm x 35.8cm x 24.0cm, 2.4kg
- 17": 2.49cm x 39.6cm x 26.2cm, 2.8kg

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

## exterior-keyboard-touchpad-overview：External Overview / Keyboard & touchpad overview

[Figure: Keyboard & Touchpad | keyboard-touchpad.webp]

来源章节：External Overview / Keyboard & touchpad overview

## exterior-materials：External Overview / Materials

|Part                              |Material |
|----------------------------------|---------|
|LCD back cover (lid)              |Aluminum |
|LCD front cover (bezel)           |Plastic  |
|Top case (palm rests/port covers) |Plastic  |
|Bottom panel                      |Aluminum |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

The Serval WS has the following LED indicators:

|Icon                                    |Color          |Description                      |
|----------------------------------------|---------------|---------------------------------|
|[Figure: Power LED | led-power.png]       |Orange         |Powered off, DC power plugged in |
|                                        |Green          |Powered on                       |
|                                        |Blinking green |Sleeping (suspended)             |
|[Figure: Battery LED | led-battery.png]   |Orange         |Battery charging                 |
|                                        |Green          |Battery fully charged            |
|                                        |Blinking orange|Battery critically low           |
|[Figure: Storage LED | led-storage.png]   |Green          |Storage drive activity           |

### Keyboard Shortcuts

The Serval WS has the following actions available using the Fn and Function keys:

|Key                        |Shortcut|Action                             |
|---------------------------|--------|-----------------------------------|
|[Figure: Fn-F1 | fn-f1.png]  |Fn+F1   |Toggle trackpad                    |
|[Figure: Fn-F2 | fn-f2.png]  |Fn+F2   |Toggle built-in LCD                |
|[Figure: Fn-F3 | fn-f3.png]  |Fn+F3   |Mute                               |
|[Figure: Fn-F5 | fn-f5.png]  |Fn+F5   |Volume down                        |
|[Figure: Fn-F6 | fn-f6.png]  |Fn+F6   |Volume up                          |
|[Figure: Fn-F7 | fn-f7.png]  |Fn+F7   |Toggle displays                    |
|[Figure: Fn-F8 | fn-f8.png]  |Fn+F8   |Screen brightness down             |
|[Figure: Fn-F9 | fn-f9.png]  |Fn+F9   |Screen brightness up               |
|[Figure: Fn-F10 | fn-f10.png]|Fn+F10  |Toggle webcam                      |
|[Figure: Fn-F11 | fn-f11.png]|Fn+F11  |Toggle airplane mode               |
|[Figure: Fn-F12 | fn-f12.png]|Fn+F12  |Suspend                            |
|[Figure: Fn-F12 | fn-dia.jpg]|Fn+`    |Play/Pause                         |
|[Figure: Fn-* | fn-star.png] |Fn+*    |Toggle keyboard backlight          |
|[Figure: Fn-/ | fn-slash.png]|Fn+/    |Cycle keyboard color               |
|[Figure: Fn-- | fn-minus.png]|Fn+-    |Decrease keyboard brightness       |
|[Figure: Fn-+ | fn-plus.png] |Fn++    |Increase keyboard brightness       |
|1                          |Fn+1    |Toggle fan between max/automatic   |

来源章节：External Overview / LED indicators

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- LCD panel connector is highlighted in light green
- Webcam/microphone connector is highlighted in cyan
- Keyboard adhesive access point is highlighted in pink
- RAM is highlighted in maroon
- BIOS flash chip (U46) is highlighted in black
- Fan connectors are highlighted in orange
- M.2 SSDs are highlighted in dark green
    - Both slots support PCIe NVMe Gen 4
- Speaker connectors are highlighted in yellow
    - Right speaker connector (on the left) is under the outermost SSD
- CMOS battery connector (under the outermost SSD) is highlighted in purple
- Wireless card (under the innermost SSD) is highlighted in blue
- Touchpad connector is highlighted in white
- Main battery connector is highlighted in red

来源章节：Internal Overview / Bottom components and connectors

## interior-under-keyboard-components-and-connectors：Internal Overview / Under-keyboard components and connectors

[Figure: Under-Keyboard Connectors | under-keyboard.webp]

- Keyboard connector is highlighted in yellow
- Keyboard backlight connector is highlighted in green
- Power button/LED connector is highlighted in red
- Front LED connector is highlighted in cyan

来源章节：Internal Overview / Under-keyboard components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Serval WS can be upgraded or replaced as necessary. This page uses photos of the 15" model, but screw counts, general component locations, and other details are the same on the 17" model unless otherwise noted.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 15 bottom panel screws.
    - All screws are the same length on any given unit. However, the 17" model uses longer screws than the 15" model.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Pull the bottom panel off, starting from the hinges in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-battery：Parts & Repairs / Replacing the battery

The battery provides primary power whenever the system is unplugged.

The model and part numbers for the Serval WS 13's battery are dependent on the screen size:

- For the 15" model, the battery model is `PD50BAT-6-80` and the original part number is `6-87-PD50S-82B01`.
- For the 17" model, the battery model is `PC70BAT-6-80` and the original part number is `6-87-PD70S-82B01`.

Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number. You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the three silver battery screws along the bottom of the battery.

[Figure: Battery screws | battery.webp]

3. Unplug the white connector (highlighted cyan above) connecting the battery to the motherboard.
4. Remove the battery, starting from the bottom edge with screw holes.
5. When putting in the new battery, start with the plastic tabs along the top edge (opposite from the screw holes.)
6. When plugging in the new battery, the red wire on the connector goes on the left, and the black wire goes on the right.

来源章节：Parts & Repairs / Replacing the battery

## repairs-replacing-the-keyboard：Parts & Repairs / Replacing the keyboard

The keyboard can be replaced using the instructions below.

**Tools required:** Cross-head (Phillips) screwdriver; tweezers (optional)
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the keyboard:

1. Follow the steps above to remove the bottom cover.
2. Pull the keyboard adhesive out of the machine to detatch it from the keyboard.
    - The adhesive strip access point is highlighted green below.
    - If the end of the keyboard adhesive strip is tucked behind the fan, it can be pulled out using tweezers or a small screwdriver, or the thermal system can be removed to expose it.
        - Replacing the thermal paste is recommended if the thermal system is removed.
    - If the adhesive strip breaks, remove as much of it as possible. The keyboard can be reinstalled without the adhesive strip.

[Figure: Keyboard adhesive & push point | keyboard-adhesive.webp]

3. Open the lid slightly and place the machine on its side.
4. Push the screwdriver into the keyboard push point (highlighted red above) until the keyboard pops out.

[Figure: Keyboard push point | keyboard-push-point.webp]

5. Set the machine back down and raise the keyboard away from the chassis. The larger ribbon cable is for the keyboard, while the smaller ribbon cable is for the keyboard backlight.

[Figure: Keyboard ribbons | keyboard-ribbons.webp]

6. Flip the black latches upwards to free the ribbon cables.
7. Pull the ribbon cables out of the connectors.
8. Remove the keyboard and replace it with the new one.
9. Carefully slide both ribbon cables into their connectors.
10. Flip the black latches back into place to secure the ribbon cables.
11. (Optional) Replace the keyboard adhesive strip on the chassis.
12. Place the keyboard back into position, starting with the tabs on the bottom edge.
13. Secure the rest of the keyboard by pressing down on each of its edges.
14. Turn the machine lid-side down again.
15. Replace the bottom panel.

来源章节：Parts & Repairs / Replacing the keyboard

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Serval WS 13 supports up to 96GB (2x48GB) of DDR5 SO-DIMMs running at 5600MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the RAM:

1. Follow the steps above to remove the bottom cover.
2. Press the small tabs on both sides of the RAM simultaneously. The RAM should spring up to an angle.

[Figure: RAM removal | ram-slots.webp]

3. Remove the RAM from the slot.
4. Insert the new RAM (or reseat the existing RAM) by placing it in the keyed slot and pressing down on the RAM until it clicks into place.

来源章节：Parts & Repairs / Replacing the RAM

## repairs-replacing-an-m-2-nvme-ssd：Parts & Repairs / Replacing an M.2/NVMe SSD

This model supports up to two M.2 SSDs. Both M.2 slots are size 2280 and support PCIe NVMe Generation 4.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the M.2 drive:

1. Follow the steps above to remove the bottom cover.
2. Unscrew the retainer screw opposite the M.2 slot.

[Figure: M.2 slots | m2-slots.webp]

3. Remove the existing M.2 drive by pulling it out of the slot.
4. Insert the new M.2 drive into the slot and hold it in place.
5. Replace the retainer screw.

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Serval WS 13 has a single heatsink assembly with two fans. This assembly cools the CPU and GPU.

If the fans become noisy and cleaning them out doesn't fix the issue, you may need a new fan. [Contact support](https://support.system76.com) to start a warranty claim or parts purchase. These instructions can also be used if physical damage to the heatsink necessitates its replacement.

Thermal paste helps facilitate effective heat transfer between the CPU/GPU and the cooling equipment; depending on your climate and the age of the machine, replacing the thermal paste may improve cooling performance. The thermal paste should generally be replaced whenever the heatsink is removed.

Thermal putty (thicker than thermal paste) is used to bridge the gap between the VRAM chips and the heatsink. Replacing the thermal putty is optional when removing the heatsink. System76 suggests [Thermal Grizzly Putty Basic](https://www.thermal-grizzly.com/en/tg-putty/s-tg-p-b-030) (available at various retailers) or a similar alternative.

**Tools required:** Cross-head (Phillips) screwdriver, thermal putty spreader (flat plastic tool)
**Time estimate:** 15 minutes
**Difficulty:** High ●

### Steps to replace the heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Remove the thirteen heatsink and fan screws in order of the stamped numbers, starting with #1, then #2, and continuing until you have removed #13.
    - The black screws (#1-#12) are held captive, and will not completely detatch from the heatsink/fans. The silver screw (#13) is not held captive, and will come loose when untightened.
    - Do not remove the smaller black screws holding the fan covers onto the fans.

[Figure: Thermal screws | thermal-screws.webp]

3. Unplug the white fan connectors from the motherboard.
4. Remove the heatsink/fans from the case, being careful not to bend the heatsink pipes. It may take some pressure to break the seal of the thermal paste.
5. Using a paper towel, remove the existing thermal paste from the CPU, GPU, and heatsink. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.
    - You can optionally remove the thermal putty from the four VRAM chips surrounding the GPU chip if you have replacement thermal putty to install.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

6. Apply a small line of thermal paste directly onto the CPU chip and the GPU chip.
    - If you're also replacing the thermal putty, apply the new putty to the four VRAM chips.
    - If you aren't replacing the thermal putty, scoop the existing putty (from the VRAM chip and the corresponding location on the heatsink) into the center of each VRAM chip using a flat plastic tool.

[Figure: Thermal paste application | thermal-paste-application.webp]

7. Carefully replace the heatsink.
8. Replace the fan and heatsink screws, starting with #1, then #2, and so on until #13.
9. Plug the white fan connectors back into the motherboard.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your Serval WS's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the bottom cover.
2. If there is an M.2 SSD in the slot closest to the battery, follow the steps above to remove the M.2 SSD.
    - The other SSD can optionally be removed to make working with the wireless card easier.
3. Locate the wireless module. Remove any clear tape that is securing the wires.

[Figure: Wireless card | wireless-card.webp]

4. Gently remove the two antennas (highlighted red above) by pulling them up and away from the wireless card.
5. Remove the retaining screw opposite the M.2 slot, highlighted yellow above.
6. Slide the card out of the M.2 slot, parallel with the motherboard.
7. Insert the new wireless card into the M.2 slot.
8. Replace the retaining screw.
9. Attach the two antennas by aligning the circular fittings and pressing onto the wireless card. The connectors will snap into place. _Use caution when attaching the connectors; the pins can bend, break, or snap._
10. Replace the M.2 SSD, bottom cover, and battery.

来源章节：Parts & Repairs / Replacing the wireless card

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the comptuer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover, unplug the main battery, and remove the outermost SSD.
2. Remove the innermost SSD and remove the wireless card, then pull the battery away from the adhesive holding it to the case.
    - If you are not replacing the CMOS battery, then this step is optional.

[Figure: CMOS battery | cmos-battery.webp]

3. Unplug the white connector that connects the CMOS battery to the motherboard.
4. To clear the CMOS, open the lid of the machine and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
5. Re-connect the CMOS battery and the main battery.
6. Replace the SSDs and wireless card (if necessary) and the bottom panel.
7. Power up the Serval WS. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be removed and replaced individually.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the left speaker:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the speaker connector from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Left speaker wire and screws | speaker-left.webp]

3. Unscrew the two speaker screws, free the speaker wire from the channels in the chassis, and remove the speaker.
4. Screw in the new speaker and connect it to the motherboard, securing the wire using the channels in the chassis.
5. Replace the bottom cover.

### Steps to replace the right speaker:

1. Follow the steps above to remove the bottom cover and remove the outermost SSD.
2. Peel back any clear tape hanging onto the speaker from the wireless card.
3. Disconnect the speaker connector from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Right speaker wire and screws | speaker-right.webp]

4. Unscrew the three speaker screws and remove the speaker.
5. Screw in the new speaker and connect it to the motherboard.
6. Replace the outermost SSD (if necessary) and bottom cover.

来源章节：Parts & Repairs / Replacing the speakers
