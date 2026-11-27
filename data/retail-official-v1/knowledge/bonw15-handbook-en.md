# System76 Bonobo WS (bonw15 & bonw15-b) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/bonw15/specs/
来源 https://system76.com/tech-docs/models/bonw15/external-overview/
来源 https://system76.com/tech-docs/models/bonw15/internal-overview/
来源 https://system76.com/tech-docs/models/bonw15/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Bonobo WS | bonw15.webp]

The System76 Bonobo WS is a laptop with the following specifications:

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

- GigaDevice GD25B256EYIGR flash chip
    - WSON-8 form factor
- UEFI firmware dependent on model:
    - bonw15-b: Programmed with System76 firmware (non-open)
    - bonw15: Programmed with [System76 Open Firmware](https://github.com/system76/firmware-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5570E
- EC firmware dependent on model:
    - bonw15-b: Programmed with non-open EC firmware
    - bonw15: Programmed with [System76 EC](https://github.com/system76/ec)

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- GPU options:
    - NVIDIA GeForce RTX 4090
        - TGP: 150W (175W boost)
    - NVIDIA GeForce RTX 4080
        - TGP: 150W (175W boost)
- eDP display options:
    - 17.3" 3840x2160@144Hz LCD
        - LCD panel: BOE NE173QUM-NY1 (or equivalent)
    - 17.3" 2560x1440@240Hz LCD
        - LCD panel: BOE NE173QHM-NZ1 (or equivalent)
- External video outputs:
    - 1x HDMI 2.1
    - 1x Mini DisplayPort 1.4
    - 2x DisplayPort 1.4 over USB-C

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

- 330W (19.5V, 16.92A) DC-in ports
    - DC-in port accepts rectangular (not barrel) connector
- Included AC adapter: Chicony A20-330P1A
    - AC power cord type: IEC C13
- 99Wh 8-cell Lithium-Ion battery
    - Model number: X270BAT-8-99

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- Combined microphone & S/PDIF (optical) 3.5mm jack
- HDMI, Mini DisplayPort, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 3x M.2 PCIe NVMe Gen 4

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 2x USB Type-C with Thunderbolt
    - Thunderbolt version dependent on model:
        - bonw15-b: Thunderbolt 5
        - bonw15: Thunderbolt 4
- 2x USB 3.2 Gen 2 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 2.49cm x 39.6cm x 27.8cm, 3.29kg

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

|Part                              |Material |Part Number        |
|----------------------------------|---------|-------------------|
|LCD back cover (lid)              |Aluminum |6-39-X3701-022     |
|LCD front cover (bezel)           |Plastic  |6-40-X3701-010     |
|Top case (palm rests/port covers) |Aluminum |6-78-X370SNWG2-010 |
|Bottom panel                      |Aluminum |6-39-X3703-011     |
|Back strip                        |Plastic  |6-42-X3703-101     |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

The Bonobo WS has the following LED indicators:

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

The Bonobo WS has the following actions available using the Fn and Function keys:

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
- Wireless card is highlighted in navy
- M.2 SSDs are highlighted in dark green
    - All slots support PCIe NVMe Gen 4
- Fan connectors are highlighted in orange
- CMOS battery connector is highlighted in purple
- Speaker connectors are highlighted in yellow
    - Left speaker connector (on the right) is under SSD slot 3
- BIOS flash chip (U58) is highlighted in black
- Main battery connector is highlighted in red
- Touchpad connector is highlighted in white
- RAM is highlighted in maroon

来源章节：Internal Overview / Bottom components and connectors

## interior-under-keyboard-components-and-connectors：Internal Overview / Under-keyboard components and connectors

[Figure: Under-Keyboard Connectors | under-keyboard.webp]

- Keyboard connector is highlighted in yellow
- Keyboard backlight connectors are highlighted in green
- Power button/LED connector is highlighted in red
- Keyboard adhesive access point is highlighted in cyan

来源章节：Internal Overview / Under-keyboard components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Bonobo WS can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (such as USB drives or Kensington locks) are unplugged or removed from the system.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 16 bottom panel screws.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Slide the back strip off the back of the machine.

[Figure: Bottom panel back strip | bottom-panel-back-strip.webp]

4. Pull the bottom panel off, starting from the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-battery：Parts & Repairs / Replacing the battery

The battery provides primary power whenever the system is unplugged.

The model number for the Bonobo WS 15's battery is `X270BAT-8-99`, and the original part number is `6-87-X270S-92B01`. Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number. You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the four screws along the top of the battery.

[Figure: Battery screws | battery.webp]

3. Unplug the white connector connecting the battery to the motherboard.
4. Remove the battery, starting from the top edge with screw holes.
5. When putting in the new battery, start with the plastic tabs along the bottom edge (opposite from the screw holes.)
6. When plugging in the new battery, the red wire on the connector goes on the left, and the black wire goes on the right.

来源章节：Parts & Repairs / Replacing the battery

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Bonobo WS 15 supports up to 96GB (2x48GB) of DDR5 SO-DIMMs running at 5600MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

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

This model supports up to three M.2 SSDs. All M.2 slots are size 2280 and support PCIe NVMe Generation 4.

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

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your Bonobo WS's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the bottom cover.
2. Locate the wireless card. Remove any clear tape that is securing the wires.

[Figure: Wireless card | wireless-card.webp]

3. Gently remove the two antennas (highlighted red above) by pulling them up and away from the wireless card.
4. Remove the retaining screw opposite the M.2 slot, highlighted yellow above.
5. The wireless card will pop up at an angle. Remove the card from the M.2 slot.
6. Insert the new wireless card into the M.2 slot at an angle.
7. Hold the card down and replace the retaining screw.
8. Attach the two antennas by aligning the circular fittings and pressing onto the wireless card. The connectors will snap into place. _Use caution when attaching the connectors; the pins can bend, break, or snap._

来源章节：Parts & Repairs / Replacing the wireless card

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the comptuer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover.
2. Unplug the white connector that connects the CMOS battery to the motherboard.
    - If you are replacing the battery, gently pull from the sides to pry it up from where it's stuck to the case.

[Figure: CMOS battery | cmos-battery.webp]

3. To clear the CMOS, open the lid of the machine and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
4. Re-connect the CMOS battery and the main battery.
5. Replace the SSDs and wireless card (if necessary) and the bottom panel.
6. Power up the Bonobo WS. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Bonobo WS 15 has a single heatsink assembly with two fans. This assembly cools the CPU and GPU.

If the fans become noisy and cleaning them out doesn't fix the issue, you may need a new fan. [Contact support](https://support.system76.com) to start a warranty claim or parts purchase. These instructions can also be used if physical damage to the heatsink necessitates its replacement.

Thermal paste helps facilitate effective heat transfer between the CPU/GPU and the cooling equipment; depending on your climate and the age of the machine, replacing the thermal paste may improve cooling performance. The thermal paste should generally be replaced whenever the heatsink is removed.

Thermal putty (thicker than thermal paste) is used to bridge the gap between the VRAM chips and the heatsink. Replacing the thermal putty is optional when removing the heatsink. System76 suggests [Thermal Grizzly Putty Basic](https://www.thermal-grizzly.com/en/tg-putty/s-tg-p-b-030) (available at various retailers) or a similar alternative.

**Tools required:** Cross-head (Phillips) screwdriverthermal putty spreader (flat plastic tool)
**Time estimate:** 15 minutes
**Difficulty:** High ●

### Steps to replace the heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover and remove any SSDs present in slots 2 and 3.
2. Remove the thirteen heatsink and fan screws in order of the stamped numbers, starting with #1, then #2, and continuing until you have removed #13.
    - Screws #1-#12 are held captive, and will not completely detatch from the heatsink/fans. Screw #13 is not held captive, and will come loose when untightened.
    - Do not remove the smaller screws holding the fan covers onto the fans.

[Figure: Thermal screws | thermal-screws.webp]

3. Remove any clear tape covering the fan wires.
    - The tape covering the wireless card antennas may also need to be removed.
4. Unplug the black fan connectors from the motherboard.
5. Remove the heatsink/fans from the case, being careful not to bend the heatsink pipes. It may take some pressure to break the seal of the thermal paste.
6. Using a paper towel, remove the existing thermal paste from the CPU, GPU, and heatsink. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.
    - You can optionally remove the thermal putty from the eight VRAM chips surrounding the GPU chip if you have replacement thermal putty to install.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

7. Apply a small line of thermal paste directly onto the CPU chip and the GPU chip.
    - If you're also replacing the thermal putty, apply the new putty to the eight VRAM chips.
    - If you aren't replacing the thermal putty, scoop the existing putty (from the VRAM chip and the corresponding location on the heatsink) into the center of each VRAM chip using a flat plastic tool.

[Figure: Thermal paste application | thermal-paste-application.webp]

8. Carefully replace the heatsink.
9. Replace the fan and heatsink screws, starting with #1, then #2, and so on until #13.
10. Plug the black fan connectors back into the motherboard and (optionally) replace the clear tape.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be removed and replaced individually.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the left speaker:

1. Follow the steps above to remove the bottom cover and remove any SSDs present in slots 2 and 3.
2. Remove the two screws holding the audio daugherboard onto the chassis, then flip the audio daughterboard over towards the motherboard.

[Figure: Audio daughterboard | audio-daughterboard.webp]

3. Disconnect the speaker connector from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Left speaker wire and screws | speaker-left.webp]

4. Unscrew the two speaker screws, free the speaker wire from the channels in the chassis, and remove the speaker.
5. Screw in the new speaker and connect it to the motherboard, securing the wire using the channels in the chassis.
6. Replace the audio daughterboard and bottom cover.

### Steps to replace the right speaker:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the speaker connector from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Right speaker wire and screws | speaker-right.webp]

3. Unscrew the two speaker screws and remove the speaker.
4. Screw in the new speaker and connect it to the motherboard.
5. Replace the bottom cover.

来源章节：Parts & Repairs / Replacing the speakers

## repairs-replacing-the-keyboard：Parts & Repairs / Replacing the keyboard

The keyboard includes per-key RGB LED backlighting, and can be replaced using the instructions below.

**Warning:** Removing the keyboard may cause permanent aesthetic damage to the underside of the keyboard, particularly if the adhesive strip is not fully removed first. Removal is not recommended unless the keyboard is malfunctioning.

**Tools required:** Cross-head (Phillips) screwdriver; tweezers (optional)
**Time estimate:** 10 minutes
**Difficulty:** High ●

### Steps to replace the keyboard:

1. Follow the steps above to remove the bottom cover.
2. Pull the keyboard adhesive out of the machine to detatch it from the keyboard.
    - The adhesive strip access point is highlighted green below.
    - If the end of the keyboard adhesive strip is tucked behind the fan, it can be pulled out using tweezers or a small screwdriver, or the thermal system can be removed to expose it.
        - Replacing the thermal paste is recommended if the thermal system is removed.
    - If the adhesive strip breaks, remove as much of it as possible. The keyboard can be reinstalled without the adhesive strip.

[Figure: Keyboard adhesive & push point | keyboard-adhesive.webp]

3. Open the lid slightly and place the machine on its side.
4. Push a screwdriver into the keyboard push point (highlighted red above) until the keyboard pops out.

[Figure: Keyboard push point | keyboard-push-point.webp]

5. Set the machine back down and raise the keyboard away from the chassis. The largest ribbon cable is for the keyboard, while the smaller ribbon cables are for the keyboard backlight.

[Figure: Keyboard ribbons | keyboard-ribbons.webp]

6. Flip the black latches upwards to free the ribbon cables.
7. Pull the ribbon cables out of the connectors.
8. Remove the keyboard and replace it with the new one.
9. Carefully slide all three ribbon cables into their connectors.
10. Flip the black latches back into place to secure the ribbon cables.
11. (Optional) Replace the keyboard adhesive strip on the chassis.
12. Place the keyboard back into position, starting with the tabs on the bottom edge.
13. Secure the rest of the keyboard by pressing down on each of its edges.
14. Turn the machine lid-side down again.
15. Replace the bottom cover.

来源章节：Parts & Repairs / Replacing the keyboard
