# System76 Galago Pro (galp7) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/galp7/specs/
来源 https://system76.com/tech-docs/models/galp7/external-overview/
来源 https://system76.com/tech-docs/models/galp7/internal-overview/
来源 https://system76.com/tech-docs/models/galp7/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Galago Pro | galp7.webp]

The System76 Galago Pro is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports Intel 13th Generation (Raptor Lake-H) CPUs
    - [Core i7-13700H](https://ark.intel.com/content/www/us/en/ark/products/232128/intel-core-i713700h-processor-24m-cache-up-to-5-00-ghz.html)
    - [Core i5-13500H](https://ark.intel.com/content/www/us/en/ark/products/232147/intel-core-i513500h-processor-18m-cache-up-to-4-70-ghz.html)

来源章节：Specifications / CPU

## specs-bios：Specifications / BIOS

- Macronix  flash chip
    - WSON-8 form factor
- Programmed with [System76 Open Firmware](https://github.com/system76/firmware-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5570E
- Programmed with [System76 EC](https://github.com/system76/ec)

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- GPU options:
    - Intel Iris Xe Graphics
- eDP display: 14.1" 1920x1080@144Hz LCD
    - LCD panel: Sharp LQ140M1JW49 (or equivalent)
- External video outputs:
    - 1x HDMI 2.1
    - 1x DisplayPort 1.4 over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- Up to 64GB (2x32GB) dual-channel DDR4 SO-DIMMs @ 3200 MHz

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- Gigabit Ethernet
- M.2 PCIe/CNVi WiFi/Bluetooth
    - Intel Wi-Fi 6E AX210/AX211
        - Bluetooth 5.3

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 90W (19V, 4.74A) DC-in port
    - Barrel size: 5.5mm (outer), 2.5mm (inner)
- Included AC adapter: Chicony A16-090P1A
    - AC power cord type: IEC C5
- USB-C charging compatible with 90W+ charger
- 53Wh 4-cell Lithium-Ion battery
    - Model number: NV40BAT-4-53

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- 3.5mm headphone/microphone combo jack
- HDMI, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 1x M.2 (PCIe NVMe Gen 4) SSD
- SD card reader

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB Type-C with Thunderbolt 4
    - Supports DisplayPort over USB-C
    - Supports USB-C charging (USB-PD)
- 1x USB 3.2 (Gen 2) Type-C
- 2x USB 3.2 (Gen 1) Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 32.49cm x 22.5cm x 1.82cm, 1.45kg

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

|Part                              |Material |Part Number       |
|----------------------------------|---------|------------------|
|LCD back cover (lid)              |Aluminum |6-39-NV411-023    |
|LCD front cover (bezel)           |Plastic  |6-40-NV401-011    |
|Top case (palm rests/port covers) |Plastic  |6-78-NV41RZ02-010 |
|Bottom panel                      |Plastic  |6-39-NV4K3-011    |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

The Galago Pro has the following LED indicators:

|Icon                                    |Color          |Description                                      |
|----------------------------------------|---------------|-------------------------------------------------|
|[Figure: Power LED | led-power.png]       |Orange         |Powered off, DC power plugged in                 |
|                                        |Green          |Powered on                                       |
|                                        |Blinking green |Sleeping (suspended)                             |
|[Figure: Battery LED | led-battery.png]   |Orange         |Battery charging                                 |
|                                        |Green          |Battery fully charged                            |
|                                        |Blinking orange|Battery critically low                           |

### Keyboard Shortcuts

The Galago Pro has the following actions available using the Fn and Function keys:

|Key                           |Shortcut|Action                             |
|------------------------------|--------|-----------------------------------|
|[Figure: Fn-F1 | fn-f1.png]     |Fn+F1   |Toggle trackpad                    |
|[Figure: Fn-F2 | fn-f2.png]     |Fn+F2   |Toggle built-in LCD                |
|[Figure: Fn-F3 | fn-f3.png]     |Fn+F3   |Mute                               |
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

来源章节：External Overview / LED indicators

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- RAM slots are highlighted in purple
- M.2 SSD is highlighted in maroon
- Wireless card is highlighted in yellow
- CMOS battery connector is highlighted in pink
- Webcam/microphone connector is highlighted in cyan
- BIOS flash chip (U43) is highlighted in black
- Fan connectors are highlighted in red
- Speaker connectors are highlighted in dark green
- Main battery connector is highlighted in orange
- Touchpad connector is highlighted in white
- LCD panel connector is highlighted in light green

来源章节：Internal Overview / Bottom components and connectors

## interior-under-keyboard-components-and-connectors：Internal Overview / Under-keyboard components and connectors

[Figure: Under-Keyboard Connectors | under-keyboard.webp]

- Keyboard connector is highlighted in green
- Keyboard backlight connector is highlighted in red
- EC chip is highlighted in cyan
  - ITE IT5570E-128

来源章节：Internal Overview / Under-keyboard components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Galago Pro can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 14 bottom panel screws.
    - The two hinge corner screws (highlighted red below) are slightly thicker than the rest.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Pull the bottom panel off, starting from the hinges and vents in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-removing-the-battery：Parts & Repairs / Removing the battery

The battery provides primary power whenever the system is unplugged.

The model number for the Galago Pro 7's battery is `NV40BAT-4-53`, and the original part number is `6-87-NV4PS-54B01`. Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number. You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to remove the battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the five battery screws, highlighted green below.

[Figure: Battery screws | battery.webp]

3. Unplug the battery connector (highlighted yellow above) and lift the battery out of the chassis.

来源章节：Parts & Repairs / Removing the battery

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Galago Pro 7 supports up to 64GB (2x32GB) of DDR4 SO-DIMMs running at 3200MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the RAM:

1. Follow the steps above to remove the bottom cover.
2. Press the small tabs on both sides of the RAM simultaneously. The RAM should spring up to an angle.

[Figure: RAM slots | ram-slots.webp]

3. Remove the RAM from the slot.
4. Insert the new RAM (or reseat the existing RAM) by placing it in the keyed slot and pressing down until it clicks into place.

来源章节：Parts & Repairs / Replacing the RAM

## repairs-replacing-an-m-2-nvme-ssd：Parts & Repairs / Replacing an M.2/NVMe SSD

This model supports one M.2 SSD of size 2280 with a PCIe NVMe Generation 4 interface.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the M.2 drive:

1. Follow the steps above to remove the bottom cover.
2. Unscrew the retainer screw opposite the M.2 slot.

[Figure: M.2 slot | m2-slot.webp]

3. Remove the existing M.2 drive by pulling it out of the slot.
4. Insert the new M.2 drive into the slot and hold it in place.
5. Replace the retainer screw.

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your Galago Pro's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 12 minutes
**Difficulty:** Medium ●

### Steps to replace the WiFi/Bluetooth module:

1. Follow the steps above to remove the bottom cover.
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

1. Follow the steps above to remove the bottom cover.
2. Follow the steps above to remove the M.2 drive, then pull the battery away from the adhesive holding it to the case.
    - If you are not replacing the CMOS battery, this step is optional, but will make it easier to unplug the CMOS battery connector.

[Figure: CMOS battery | cmos-battery.webp]

3. Unplug the white connector attaching the CMOS battery to the motherboard (highlighted green above.)
4. To clear the CMOS, disconnect the main battery, open the lid of the machine, and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
5. Re-connect the CMOS battery and main battery, and replace the M.2 drive and bottom panel.
6. Power up the machine. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be removed and replaced individually.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 15 minutes
**Difficulty:** Easy ●

### Steps to replace the speakers:

1. Follow the steps above to remove the bottom cover. For the right speaker, also remove the battery and remove the SSD.
2. Unscrew the speaker screws.
    - Each speaker has two screws, which are set inside of rubber rings to reduce vibration.

[Figure: Speaker screws and connectors | speakers.webp]

3. Disconnect the speaker wire from the motherboard and lift the speaker out of the machine.
    - The speaker wire may be partially routed behind other components or tape; be careful when removing it.
4. Place the new speaker in the machine, plug in the connector, replace the screws, and reinstall the SSD and battery (if necessary) and bottom panel.

来源章节：Parts & Repairs / Replacing the speakers

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Galago Pro 7 has a single heatsink with two fans for the CPU and integrated GPU. One fan is attached to the heatsink, while the other can be replaced independently.

If a fan becomes noisy and cleaning it out doesn't fix the issue, you may need a new fan. [Contact support](https://support.system76.com) to start a warranty claim or parts purchase.

Depending on your climate and the age of the machine, it may be necessary to apply new thermal paste between the CPU and the heatsink. Thermal paste helps facilitate effective heat transfer between the CPU and the cooling equipment. These instructions can also be used in the unlikely event that your heatsink needs to be replaced.

**Tools required:** Cross-head (Phillips) screwdriver, thermal paste
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the additional fan:

1. Follow the steps above to remove the bottom cover.
2. Remove any clear tape that is securing the fan wire.
3. Unplug the white fan connector for the additional fan, highlighted green below.

[Figure: Fan screws | additional-fan.webp]

4. Remove the two fan screws, highlighted red above.
5. Replace the fan, screw in the two fan screws, and plug the white fan connector into the system.

### Steps to replace the heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover and remove the additional fan.
    - Removing the additional fan is optional, but is recommended to ensure the fan properly overlaps the heatsink's air guide after reinstallation.
2. Unplug the remaining fan connector, highlighted green below.

[Figure: Heatsink screws | heatsink-screws.webp]

3. Remove the two fan screws for the remaining fan, highlighted red above.
4. Remove the four heatsink screws (highlighted cyan above) in order, starting with #5, then #6, and continuing until #8.
    - Screws #1-4 are for the dedicated GPU portion of the heatsink, and are not installed on the Galago Pro 7.
5. Remove the heatsink from the system, being careful not to bend the heatsink pipes. It may take some pressure to break the seal of the thermal paste.
6. Using a paper towel, remove the existing thermal paste. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

7. After cleaning the CPU and heatsink, apply a small line of thermal paste directly onto each die of the CPU chip.

[Figure: Thermal paste application | thermal-paste-application.webp]

8. Carefully replace the heatsink.
9. Replace the four heatsink screws, starting with #5, then #6, and continuing until #8.
10. Screw in the attached fan and plug its connector back into the motherboard.
10. Replace the additional fan and bottom panel.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-keyboard：Parts & Repairs / Replacing the keyboard

The keyboard can be replaced using the instructions below.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to replace the keyboard:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the two keyboard screws, highlighted green below.

[Figure: Keyboard screws | keyboard-screws.webp]

3. Open the lid slightly and place the machine on its side.
4. Push the screwdriver into the keyboard push point (highlighted red above) until the keyboard pops out.

[Figure: Keyboard push point | keyboard-push-point.webp]

5. Set the machine upright and flip the keyboard down onto the touchpad. The larger ribbon cable is for the keyboard, while the smaller ribbon cable is for the keyboard backlight.

[Figure: Keyboard ribbons | keyboard-ribbons.webp]

6. Flip the black latches away from the white connectors to free the ribbon cables.
7. Pull the ribbon cables out of the connectors.
8. Remove the keyboard and replace it with the new one.
9. Carefully slide both ribbon cables into their connectors.
10. Flip the black latches back into place to secure the ribbon cables.
11. Place the keyboard back into position, starting with the tabs on the bottom edge.
12. Secure the rest of the keyboard by pressing down on each of its edges.
13. Turn the machine lid-side down again and replace the two keyboard screws.

来源章节：Parts & Repairs / Replacing the keyboard
