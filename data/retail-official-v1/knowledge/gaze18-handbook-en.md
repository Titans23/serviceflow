# System76 Gazelle (gaze18) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/gaze18/specs/
来源 https://system76.com/tech-docs/models/gaze18/external-overview/
来源 https://system76.com/tech-docs/models/gaze18/internal-overview/
来源 https://system76.com/tech-docs/models/gaze18/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Gazelle | gaze18.webp]

The System76 Gazelle is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports Intel Raptor Lake CPUs
    - [Intel i9-13900H](https://ark.intel.com/content/www/us/en/ark/products/232135/intel-core-i913900h-processor-24m-cache-up-to-5-40-ghz.html)

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

- Dedicated GPU:
    - NVIDIA GeForce RTX 3050
        - TGP: 70W (95W boost)
- Integrated GPU:
    - Intel Iris Xe Graphics
- eDP display:
    - 17.3" 1920x1080@144Hz LCD
        - LCD panel options:
            - BOE NV173FHM-N4C
            - BOE NE173FHM-NX1
            - BOE NV173FHM-NY2
            - BOE NE173QHM-NY3
            - ...or other equivalent
    - 15.6" 1920x1080@144Hz LCD
        - LCD panel options:
            - BOE NV156FHM-N4K
            - BOE NV156FHM-NX4
            - BOE NE156QHM-NY5
            - Innolux N156HCE-EN1
- External video output:
    - 1x HDMI 2.1
    - 1x Mini DisplayPort 1.4

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- Up to 64GB (2x32GB) dual-channel DDR4 SO-DIMMs @ 3200 MHz

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- Gigabit Ethernet
    - Realtek [RTL8111H](https://www.realtek.com/en/products/communications-network-ics/item/rtl8111h-s-cg)
- M.2 PCIe/CNVi WiFi/Bluetooth
    - Intel Wi-Fi 6E AX210/AX211

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 150W DC-in port
    - Barrel size: 5.5mm (outer), 2.5mm (inner)
- Included AC adapter: LiteOn PA-1151-76
    - AC power cord type: IEC C5
- 54Wh 4-cell Lithium-Ion battery
    - Model number: NP50BAT-4-54

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- Dedicated microphone 3.5mm jack
- HDMI, Mini DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 1x M.2 (PCIe NVMe Gen 4)
- 1x M.2 (PCIe NVMe Gen 3)
- MicroSD card reader

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 2x USB 3.2 Gen 2 Type-C
    - Does not support DisplayPort over USB-C, USB-C charging (USB-PD), or Thunderbolt
- 1x USB 3.2 Gen 2 Type-A
- 1x USB 2.0 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 15": 35.95cm x 23.8cm x 2.27cm, 1.99kg
- 17": 39.69cm x 26.2cm x 2.5cm, 2.41kg

来源章节：Specifications / Dimensions

## exterior-left-side-overview：External Overview / Left side overview

[Figure: 15" Left Ports | ports-left.webp]

来源章节：External Overview / Left side overview

## exterior-right-side-overview：External Overview / Right side overview

[Figure: 15" Right Ports (3060) | ports-right.webp]

编者核对：厂商图注标作 15 英寸 3060 配置，本代号规格页列出 RTX 3050。该图不用于判定此配置的右侧接口。

来源章节：External Overview / Right side overview

## exterior-front-side-overview：External Overview / Front side overview

[Figure: 15" Front Ports | ports-front.webp]

来源章节：External Overview / Front side overview

## exterior-back-side-overview：External Overview / Back side overview

[Figure: 15" Back Ports | ports-back.webp]

来源章节：External Overview / Back side overview

## exterior-keyboard-touchpad-overview：External Overview / Keyboard & touchpad overview

[Figure: Keyboard & Touchpad | keyboard-touchpad.webp]

来源章节：External Overview / Keyboard & touchpad overview

## exterior-materials：External Overview / Materials

|Part                              |Material |
|----------------------------------|---------|
|LCD back cover (lid)              |Plastic  |
|LCD front cover (bezel)           |Plastic  |
|Top case (palm rests/port covers) |Plastic  |
|Bottom panel                      |Plastic  |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

The Gazelle has the following LED indicators:

|Icon                                    |Color          |Description                      |
|----------------------------------------|---------------|---------------------------------|
|[Figure: Power LED | led-power.png]       |Orange         |Powered off, DC power plugged in |
|                                        |Green          |Powered on                       |
|                                        |Blinking green |Sleeping (suspended)             |
|[Figure: Battery LED | led-battery.png]   |Orange         |Battery charging                 |
|                                        |Green          |Battery fully charged            |
|                                        |Blinking orange|Battery critically low           |
|[Figure: Storage LED | led-storage.png]   |Green          |Storage drive activity           |

来源章节：External Overview / LED indicators

## exterior-keyboard-shortcuts：External Overview / Keyboard shortcuts

The Gazelle has the following actions available using the Fn and Function keys:

|Key                        |Shortcut|Action                          |
|---------------------------|--------|--------------------------------|
|[Figure: Fn-F1 | fn-f1.png]  |Fn+F1   |Toggle trackpad                 |
|[Figure: Fn-F2 | fn-f2.png]  |Fn+F2   |Toggle built-in LCD             |
|[Figure: Fn-F3 | fn-f3.png]  |Fn+F3   |Mute                            |
|[Figure: Fn-F5 | fn-f5.png]  |Fn+F5   |Volume down                     |
|[Figure: Fn-F6 | fn-f6.png]  |Fn+F6   |Volume up                       |
|[Figure: Fn-F7 | fn-f7.png]  |Fn+F7   |Toggle displays                 |
|[Figure: Fn-F8 | fn-f8.png]  |Fn+F8   |Screen brightness down          |
|[Figure: Fn-F9 | fn-f9.png]  |Fn+F9   |Screen brightness up            |
|[Figure: Fn-F10 | fn-f10.png]|Fn+F10  |Toggle webcam                   |
|[Figure: Fn-F11 | fn-f11.png]|Fn+F11  |Toggle airplane mode            |
|[Figure: Fn-F12 | fn-f12.png]|Fn+F12  |Suspend                         |
|[Figure: Fn-F12 | fn-dia.jpg]|Fn+`    |Play/Pause                      |
|[Figure: Fn-* | fn-star.png] |Fn+*    |Toggle keyboard backlight       |
|[Figure: Fn-/ | fn-slash.png]|Fn+/    |Cycle keyboard color            |
|[Figure: Fn-- | fn-minus.png]|Fn+-    |Decrease keyboard brightness    |
|[Figure: Fn-+ | fn-plus.png] |Fn++    |Increase keyboard brightness    |
|1                          |Fn+1    |Toggle fan between max/automatic|

来源章节：External Overview / Keyboard shortcuts

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- LCD panel connector is highlighted in light green
- Keyboard adhesive access point is highlighted in pink
- Fan connectors are highlighted in red
- Webcam/microphone connector is highlighted in yellow
- Speaker connectors are highlighted in cyan
- Main battery connector is highlighted in orange
- Wireless card is highlighted in blue
- RAM is highlighted in maroon
- BIOS flash chip (U24) is highlighted in black
- Touchpad connector is highlighted in white
- M.2 SSDs are highlighted in dark green
    - Innermost slot supports PCIe NVMe Gen 4
    - Outermost slot supports PCIe NVMe Gen 3
- CMOS battery connector is highlighted in purple

来源章节：Internal Overview / Bottom components and connectors

## interior-under-keyboard-components-and-connectors：Internal Overview / Under-keyboard components and connectors

[Figure: Under-Keyboard Connectors | under-keyboard.webp]

- Keyboard adhesive access point is highlighted in green
- Keyboard connector is highlighted in yellow
- Keyboard backlight connector is highlighted in cyan
- Power button/LED connector is highlighted in red

来源章节：Internal Overview / Under-keyboard components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Gazelle can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 5 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 13 bottom panel screws.

[Figure: Bottom panel screws | bottom-panel-screws.webp]

3. Pull the bottom panel off, starting from over the ports in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Gazelle 18 supports up to 64GB (2x32GB) of DDR4 SO-DIMMs running at 3200MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 7 minutes
**Difficulty:** Easy ●

### Steps to replace the RAM:

1. Follow the steps above to remove the bottom cover.
2. Pull the small tabs on both sides of the RAM simultaneously. The RAM should spring up to an angle.

[Figure: RAM slots | ram-slots.webp]

3. Remove the RAM from the slot.
4. Insert the new RAM (or reseat the existing RAM) by placing it in the keyed slot and pressing down until it clicks into place.

来源章节：Parts & Repairs / Replacing the RAM

## repairs-replacing-an-m-2-nvme-ssd：Parts & Repairs / Replacing an M.2/NVMe SSD

This model supports up to two M.2 SSDs. Both M.2 slots are size 2280. The inner-most slot supports PCIe NVMe Generation 4, and the outer-most slot supports PCIe NVMe Generation 3.

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

Your Gazelle's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 15 minutes
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

## repairs-replacing-the-battery：Parts & Repairs / Replacing the battery

The battery provides primary power whenever the system is unplugged.

The model number for the Gazelle 18's battery is `NP50BAT-4-54`, and the original part number is `6-87-NP5KS-51B00`. Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number. You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 8 minutes
**Difficulty:** Easy ●

### Steps to replace the battery:

1. Follow the steps above to remove the bottom cover.
2. Remove the five battery screws, highlighted yellow below.

[Figure: Battery | battery.webp]

3. Unplug the battery connector (highlighted red above) and lift the battery out of the chassis.

来源章节：Parts & Repairs / Replacing the battery

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the computer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 15 minutes
**Difficulty:** Medium ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover.
2. Follow the steps above to remove the outermost M.2 drive, then pull the battery away from the adhesive holding it to the case.
    - If you are not replacing the CMOS battery, this step is optional, but will make it easier to unplug the CMOS battery connector.
3. Unplug the white connector that connects the CMOS battery to the motherboard.

[Figure: CMOS battery | cmos-battery.webp]

4. To clear the CMOS, disconnect the main battery, open the lid of the machine, and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
5. Re-connect the CMOS battery.
6. Reconnect the main battery, replace the M.2 SSD, and replace the bottom cover.
7. Power up the Gazelle. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Gazelle 18 includes two fans as part of a single heatsink/fan assembly.

If the CPU/GPU fans become noisy and cleaning them out doesn't fix the issue, you may need a new fan. [Contact support](https://support.system76.com) to start a warranty claim or parts purchase.

Thermal paste helps facilitate heat transfer between the CPU/GPU and the cooling equipment. Depending on your climate and the age of the machine, replacing the thermal paste between the CPU/GPU and the heatsink may help the system run cooler.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the fans/heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Remove any clear tape that is securing the fan wires.
3. Remove the silver heatsink and fan screws in the order of the stamped numbers, starting with #1, then #2, and continuing until you have removed #12.
    - *Note: Do not remove the smaller black screws holding the fan covers onto the fans.*

[Figure: Thermal screws | thermal-screws.webp]

4. Unplug the two white fan connectors from the motherboard (highlighted cyan above).
5. Remove the heatsink/fans from the case, being careful not to bend the heatsink pipes. It may take some pressure to break the seal of the thermal paste.
6. Using a paper towel, remove the existing thermal paste. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

7. After cleaning the CPU, GPU, and heatsink, apply a small line of thermal paste directly onto both CPU chips and the GPU chip.

[Figure: Thermal paste application | thermal-paste-application.webp]

8. Carefully replace the heatsink.
9. Replace the silver heatsink and fan screws, starting with #1, then #2, and so on until #12.
10. Plug the two white fan connectors back into the motherboard.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-keyboard：Parts & Repairs / Replacing the keyboard

The keyboard can be replaced if its switches or electronics have been damaged.

**Tools required:** Cross-head (Phillips) screwdriver, tweezers (optional)
**Time estimate:** 10 minutes
**Difficulty:** High ●

### Steps to replace the keyboard:

1. Follow the steps above to remove the bottom cover.
2. Locate the keyboard adhesive strip at the edge of the motherboard underneath the right fan's heatsink pipe, highlighted green below.
    - You can optionally remove the heatsink to make the adhesive easier to grasp. Replacing the thermal paste is recommended if the heatsink is removed.

[Figure: Keyboard adhesive strip location | keyboard-adhesive.webp]

3. Using a pair of tweezers to reach the end of the strip (if necessary), pull out the end of the keyboard adhesive strip to stretch and detatch it.
4. Open the lid slightly and place the machine on its side.
5. Push a screwdriver into the keyboard push point (next to the RAM slot) until the keyboard pops out.

[Figure: Keyboard push point location | keyboard-push-point-location.webp]
[Figure: Keyboard push point | keyboard-push-point.webp]

6. Set the machine back down and raise the keyboard away from the chassis. The larger ribbon cable is for the keyboard, while the smaller ribbon cable is for the keyboard backlight.

[Figure: Keyboard ribbon connectors | keyboard-ribbons.webp]

7. Flip the black latches upwards to free the ribbon cables.
8. Pull the ribbon cables out of the connectors.
9. Remove the keyboard and replace it with the new one.
10. Carefully slide both ribbon cables into their connectors.
11. Flip the black latches back into place to secure the ribbon cables.
12. Place the keyboard back into position, starting with the tabs on the bottom edge.
13. Secure the rest of the keyboard by pressing down on each of its edges.
14. Turn the machine lid-side down again and replace the bottom cover.

来源章节：Parts & Repairs / Replacing the keyboard

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be removed and replaced individually.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the left speaker:

1. Follow the steps above to remove the bottom cover and remove the main battery.
2. Flip the black latches up to release both sides of the I/O daughterboard ribbon cable from the motherboard and the I/O daughterboard.

[Figure: I/O daughterboard ribbon cable | speaker-left-ribbon.webp]

3. Peel the ribbon cable away from the chassis.
    - The ribbon cable is adhered to the chassis. Pull slowly to avoid damage.
4. Disconnect the speaker connector from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Left speaker wire and posts | speaker-left.webp]

5. Pull the speaker up and off of the plastic posts, and free the speaker wire from any tape and channels in the chassis.
6. Slide the new speaker onto the plastic posts and connect it to the motherboard, securing the wire using the channels in the chassis.
7. Reconnect the ribbon cable to the motherboard and I/O daughterboard, then flip the black latches to secure both connections.
8. Replace the main battery and bottom cover.

### Steps to replace the right speaker:

1. Follow the steps above to remove the bottom cover.
    - The outermost SSD can optionally be removed to make it easier to work with the speaker plug.
2. Disconnect the speaker connector from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Right speaker wire and posts | speaker-right.webp]

3. Pull the speaker up and off of the plastic posts.
4. Slide the new speaker onto the plastic posts and connect it to the motherboard.
5. Replace the SSD (if necessary) and bottom cover.

来源章节：Parts & Repairs / Replacing the speakers
