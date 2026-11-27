# System76 Oryx Pro (oryp12) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/oryp12/specs/
来源 https://system76.com/tech-docs/models/oryp12/external-overview/
来源 https://system76.com/tech-docs/models/oryp12/internal-overview/
来源 https://system76.com/tech-docs/models/oryp12/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Oryx Pro | oryp12.webp]

The System76 Oryx Pro is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports Intel 14th Generation (Raptor Lake) CPUs
    - [Intel Core i9-14900HX](https://ark.intel.com/content/www/us/en/ark/products/235995/intel-core-i9-processor-14900hx-36m-cache-up-to-5-80-ghz.html)

来源章节：Specifications / CPU

## specs-chipset：Specifications / Chipset

- [Intel HM770](https://ark.intel.com/content/www/us/en/ark/products/232478/intel-hm770-chipset.html)

来源章节：Specifications / Chipset

## specs-bios：Specifications / BIOS

- GigaDevice GD25B256EYIGR flash chip
    - WSON-8 form factor
- Programmed with [System76 Open Firmware](https://github.com/system76/firmware-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5570E
- Programmed with [System76 EC](https://github.com/system76/ec)

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- Dedicated GPU options:
    - NVIDIA GeForce RTX 4070
        - TGP: 115W (140W boost)
    - NVIDIA GeForce RTX 4060
        - TGP: 115W (140W boost)
    - NVIDIA GeForce RTX 4050
        - TGP: 115W (140W boost)
- Integrated GPU:
    - Intel Iris Xe Graphics
- eDP display options:
    - 16" 1920x1200@165Hz LCD
        - LCD panel: BOE NV160WUM-NX2 (or equivalent)
            - Brightness: 500 nits (cd/m²)
            - sRGB color coverage: 100%
- External video output:
    - 1x HDMI 2.1
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

- 230W (20.0V, 11.5A) DC-in port
    - Barrel size: 5.5mm (outer), 2.5mm (inner)
- Included AC adapter: Lite-On PA-1231-26
    - AC power cord type: IEC C5
- 73Wh 4-cell battery
    - Model number: NV40BAT-4-73

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- Combined microphone & S/PDIF (optical) 3.5mm jack
- HDMI, Mini DisplayPort, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 2x M.2 (PCIe NVMe Gen 4)
- MicroSD card reader

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB Type-C with Thunderbolt 4
    - Supports DisplayPort over USB-C
    - Does not support USB-C charging (USB-PD)
- 1x USB 3.2 Gen 2 Type-C
- 2x USB 3.2 Gen 1 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 35.95cm x 27.3cm x 1.99cm, 2.7kg

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

[Figure: Keyboard & Touchpad | keyboard-touchpad.webp]

来源章节：External Overview / Keyboard & touchpad

## exterior-lid：External Overview / Lid

[Figure: Lid | lid.webp]

来源章节：External Overview / Lid

## exterior-materials：External Overview / Materials

|Part                              |Material                |Part Number      |
|----------------------------------|------------------------|-----------------|
|LCD back cover (lid)              |Aluminum                |6-39-PE601-023-1 |
|LCD front cover (bezel)           |Plastic                 |6-39-PE601-013   |
|Top case (palm rests/port covers) |Plastic                 |6-78-PE6S2-012   |
|Bottom panel                      |Aluminum                |6-39-PE603-014-1 |

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

|Key                        |Shortcut|Action                                         |
|---------------------------|--------|-----------------------------------------------|
|[Figure: Fn-F1 | fn-f1.png]  |Fn+F1   |Toggle trackpad                                |
|[Figure: Fn-F2 | fn-f2.png]  |Fn+F2   |Toggle microphone mute                         |
|[Figure: Fn-F3 | fn-f3.png]  |Fn+F3   |Mute                                           |
|[Figure: Fn-F5 | fn-f5.png]  |Fn+F5   |Volume down                                    |
|[Figure: Fn-F6 | fn-f6.png]  |Fn+F6   |Volume up                                      |
|[Figure: Fn-F7 | fn-f7.png]  |Fn+F7   |Toggle displays                                |
|[Figure: Fn-F8 | fn-f8.png]  |Fn+F8   |Screen brightness down                         |
|[Figure: Fn-F9 | fn-f9.png]  |Fn+F9   |Screen brightness up                           |
|[Figure: Fn-F10 | fn-f10.png]|Fn+F10  |Toggle webcam                                  |
|[Figure: Fn-F11 | fn-f11.png]|Fn+F11  |Toggle airplane mode                           |
|[Figure: Fn-F12 | fn-f12.png]|Fn+F12  |Suspend                                        |
|[Figure: Fn-F12 | fn-dia.jpg]|Fn+`    |Play/Pause                                     |
|[Figure: Fn-* | fn-star.png] |Fn+*    |Toggle keyboard backlight                      |
|[Figure: Fn-/ | fn-slash.png]|Fn+/    |Cycle keyboard color                           |
|[Figure: Fn-- | fn-minus.png]|Fn+-    |Decrease keyboard brightness                   |
|[Figure: Fn-+ | fn-plus.png] |Fn++    |Increase keyboard brightness                   |
|1                          |Fn+1    |Toggle fan between max/automatic               |

来源章节：External Overview / Keyboard shortcuts

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- Webcam/microphone connector is highlighted in yellow
    - Webcam/microphone connector is underneath the heatsink
- LCD panel connector is highlighted in orange
    - LCD panel connector is underneath the heatsink
- Fan connectors are highlighted in cyan
- RAM is highlighted in dark green
- BIOS flash chip (U63) is highlighted in black
- Touchpad connector is highlighted in white
- Wireless card is highlighted in purple
- Speaker connectors are highlighted in light green
- Battery connector is highlighted in red
- M.2 SSDs are highlighted in maroon
    - Both slots support PCIe NVMe Gen 4
- CMOS battery connector is highlighted in pink
    - CMOS battery is underneath SSD slot 2 (closest to the main battery)

来源章节：Internal Overview / Bottom components and connectors

## interior-under-keyboard-components-and-connectors：Internal Overview / Under-keyboard components and connectors

[Figure: Under-Keyboard Connectors | under-keyboard.webp]

- Left I/O ribbon is highlighted in red
- Keyboard connector is highlighted in yellow
- Keyboard backlight connector is highlighted in cyan
- Power button/LED connector is highlighted in green

来源章节：Internal Overview / Under-keyboard components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Oryx Pro can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Part numbers:**
- Bottom panel: `6-39-PE603-014-1`

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 16 bottom panel screws.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Pull the bottom panel off, starting from the hinges in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Oryx Pro 12 supports up to 96GB (2x48GB) of DDR5 SO-DIMMs running at 5600MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

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

This model supports up to two M.2 SSDs. Both M.2 slots are size 2280. Both slots support PCIe NVMe Generation 4 only. The slots are backwards-compatible with PCIe NVMe Generation 3 drives, but the slots do not support SATA.

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
6. If you are using a slot for the first time, you can optionally remove the plastic backing from the thermal strip and apply it to the inner side of the bottom panel.

[Figure: M.2 slot thermal strip application | m2-thermal-strip.webp]

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-replacing-the-battery：Parts & Repairs / Replacing the battery

The battery provides primary power whenever the system is unplugged.

**Part numbers:**
- The battery's model number is `NV40BAT-4-73`, and the original part number is `6-87-NV4RS-71M00`.
    - Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number.
    - You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the five silver battery screws along the perimeter of the battery.

[Figure: Battery screws | battery.webp]

3. Unplug the white connector (highlighted yellow above) connecting the battery to the motherboard.
4. Remove the battery, starting from the bottom edge with screw holes.
5. When putting in the new battery, start with the plastic tabs along the top edge (opposite from the screw holes.)
6. When plugging in the new battery, the red wire on the connector goes on the left, and the black wire goes on the right.

来源章节：Parts & Repairs / Replacing the battery

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your Oryx Pro's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Part numbers:**
- The standard wireless card is an Intel `AX211NGW`.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the bottom cover.
2. Locate the wireless card. Remove any clear tape that is securing the wires.

[Figure: Wireless card | wireless-card.webp]

3. Gently remove the two antennas (highlighted red above) by pulling them up and away from the wireless card.
4. Remove the retaining screw opposite the M.2 slot, highlighted yellow above.
5. Keeping the wireless card parallel with the motherboard, slide the card out of the M.2 slot.
6. Insert the new wireless card into the M.2 slot.
7. Replace the retaining screw.
8. Attach the two antennas by aligning the circular fittings and pressing onto the wireless card. The connectors will snap into place. _Use caution when attaching the connectors; the pins can bend, break, or snap._

来源章节：Parts & Repairs / Replacing the wireless card

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the computer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Part numbers:**
- The CMOS battery is a standard 3V KTS CR2032W battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 15 minutes
**Difficulty:** Medium ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover and, if installed, remove the SSD from slot 2 (closest to the main battery).

[Figure: CMOS battery | cmos-battery.webp]

2. Unplug the small white connector that connects the CMOS battery to the motherboard. If you are replacing the battery, gently pull from the sides to pry it up from where it's stuck to the case.
3. To clear the CMOS, disconnect the main battery (if it's still installed), open the lid of the machine, and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
4. Reconnect the CMOS battery, reconnect the main battery, and replace the M.2 SSD in slot 2 (if necessary).
5. Replace the bottom panel and power up the Oryx Pro. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Oryx Pro 12 uses two fans and a heatsink manufactured as a single assembly.

If the fans become noisy and cleaning them out doesn't fix the issue, you may need a new fan. [Contact support](https://support.system76.com) to start a warranty claim or parts purchase. These instructions can also be used if physical damage to the heatsink necessitates its replacement.

Thermal paste helps facilitate effective heat transfer between the CPU/GPU and the cooling equipment; depending on your climate and the age of the machine, replacing the thermal paste may improve cooling performance. The thermal paste should generally be replaced whenever the heatsink is removed.

Thermal putty (thicker than thermal paste) is used to bridge the gap between the VRAM chips and the heatsink. Replacing the thermal putty is optional when removing the heatsink. System76 suggests [Thermal Grizzly Putty Basic](https://www.thermal-grizzly.com/en/tg-putty/s-tg-p-b-030) (available at various retailers) or a similar alternative.

**Part numbers:**
- Heatsink/fan assembly: YINGFAN `6-31-PE6SN-102`
- Individual fans (may not be available separately):
    - Left fan: WINMA `ECG-82090S2-0AH`
    - Right fan: WINMA `ECG-85090S1-0AH`

**Tools required:** Cross-head (Phillips) screwdriver, thermal putty spreader (flat plastic tool)
**Time estimate:** 40 minutes
**Difficulty:** High ●

### Steps to replace the fans/heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Remove any clear tape that is securing the fan wires.
3. Remove the heatsink and fan screws in the order of the stamped and stickered numbers, starting with #1, then #2, and continuing until you have removed #13.
    - The black screws are held captive and will not fully come away from the heatsink. The silver screw (#13) is not held captive.
    - Do not remove the smaller black screws holding the fan covers onto the fans.

[Figure: Thermal screws | thermal-screws.webp]

4. Unplug the two fan connectors from the motherboard (highlighted red above).
5. Remove the heatsink/fans from the case, being careful not to bend the heatsink pipes. It may take some pressure to break the seal of the thermal paste.
6. Using a paper towel, remove the existing thermal paste from the CPU chips and the GPU chip, as well as the corresponding points on the heat sink. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.
    - You can optionally remove the thermal putty from the four Samsung VRAM chips surrounding the GPU chip and the seven `R15` chips forming an "L" shape around the CPU if you have replacement thermal putty to install.
    - Some thermal paste and/or putty may already be on the motherboard around the VRAM chips, or may get onto the motherboard while cleaning the paste/putty off of the chips. Thermal paste and putty is typically not conductive; it's safer to leave excess paste on the board than to risk damaging the board with rough removal.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

7. Apply a small line of thermal paste directly onto both CPU chips and the GPU chip.
    - If you're also replacing the thermal putty, apply the new putty to the four Samsung VRAM chips and the seven `R15` chips.
    - If you aren't replacing the thermal putty, scoop the existing putty (from the chip and the corresponding location on the heatsink) into the center of each VRAM and `R15` chip using a flat plastic tool.

[Figure: Thermal paste application | thermal-paste-application.webp]

8. Carefully replace the heatsink.
9. Replace the silver heatsink and fan screws, starting with #1, then #2, and so on until #12.
10. Plug the two white fan connectors back into the motherboard.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be removed and replaced individually.

**Part numbers:**
- Left speaker: `6-23-5PE60-0L1`
- Right speaker: `6-23-5PE60-0R1`

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the speakers:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the speaker wire from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Speaker screws and connectors | speakers.webp]

3. Unscrew the speaker screws and lift the speaker out of the machine.
    - The right speaker has three screws; the left speaker has two screws.
    - The speaker wire may be partially routed through channels in the chassis; be careful when removing it.
4. Place the new speaker in the machine, plug in the connector, replace the screws, and reinstall the bottom panel.

来源章节：Parts & Repairs / Replacing the speakers

## repairs-replacing-the-keyboard：Parts & Repairs / Replacing the keyboard

The keyboard can be replaced if its switches or electronics have been damaged.

**Part numbers:**
- Keyboard: `6-PE60SNE-G-KB-MCL-US`
    - Keyboards shipped by System76 include a custom-printed Super key.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to replace the keyboard:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the two keyboard screws (highlighted yellow below).

[Figure: Keyboard screws | keyboard-screws.webp]

3. Open the lid slightly and place the machine on its side.
4. Push the screwdriver into the keyboard push point (highlighted cyan above) until the keyboard pops out.

[Figure: Keyboard push point | keyboard-push-point.webp]

5. Set the machine back down and raise the keyboard away from the chassis. The larger ribbon cable is for the keyboard, while the smaller ribbon cable is for the keyboard backlight.

[Figure: Keyboard ribbons | keyboard-ribbons.webp]

6. Flip the black latches upwards to free the ribbon cables.
7. Pull the ribbon cables out of the connectors.
8. Remove the keyboard and replace it with the new one.
9. Carefully slide both ribbon cables into their connectors.
10. Flip the black latches back into place to secure the ribbon cables.
11. Place the keyboard back into position, starting with the tabs on the bottom edge.
12. Secure the rest of the keyboard by pressing down on each of its edges.
13. Turn the machine lid-side down again and replace the two keyboard screws.

来源章节：Parts & Repairs / Replacing the keyboard
