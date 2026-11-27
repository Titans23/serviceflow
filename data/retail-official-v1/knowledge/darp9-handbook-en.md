# System76 Darter Pro (darp9) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/darp9/specs/
来源 https://system76.com/tech-docs/models/darp9/external-overview/
来源 https://system76.com/tech-docs/models/darp9/internal-overview/
来源 https://system76.com/tech-docs/models/darp9/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Darter Pro | darp9.webp]

The System76 Darter Pro is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports Intel 13th Generation (Raptor Lake) CPUs
    - [Core i7-1360P](https://ark.intel.com/content/www/us/en/ark/products/232155/intel-core-i71360p-processor-18m-cache-up-to-5-00-ghz.html)
    - [Core i5-1340P](https://ark.intel.com/content/www/us/en/ark/products/232126/intel-core-i51340p-processor-12m-cache-up-to-4-60-ghz.html)

来源章节：Specifications / CPU

## specs-bios：Specifications / BIOS

- GD25B256D flash chip
    - WSON-8 form factor
- Programmed with [System76 Open Firmware](https://github.com/system76/firmware-open)

来源章节：Specifications / BIOS

## specs-ec：Specifications / EC

- ITE IT5570E
- Programmed with [System76 EC](https://github.com/system76/ec)

来源章节：Specifications / EC

## specs-graphics：Specifications / Graphics

- GPU: Intel Iris Xe Graphics
- eDP display: 15.6" 1920x1080@60Hz LCD
    - LCD panel options:
        - Innolux N156HCA-E5B
        - BOE NV156FHM-N4M
        - BOE NV156FHM-N4V
        - BOE NV156FHM-N63
        - or other equivalent
- External video outputs:
    - 1x HDMI 2.1
    - 1x DisplayPort 1.4a over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- Up to 96GB (2x48GB) dual-channel DDR5 SO-DIMMs @ 5200 MHz

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
- 73Wh 4-cell Lithium-ion battery
    - Model number: L140BAT-4

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Internal speakers & microphone
- Combined headphone & microphone 3.5mm jack
- HDMI, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 2x M.2 (PCIe NVMe Gen 4)
- MicroSD card reader

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB Type-C with Thunderbolt 4
    - Supports DisplayPort over USB-C
    - Supports USB-C charging (USB-PD)
- 1x USB 3.2 Gen 2 Type-C
- 1x USB 3.2 Gen 2 Type-A
- 1x USB 2.0 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 1.99cm x 35.7cm x 22.05cm, 1.74kg

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

|Part                              |Material                 |Part Number        |
|----------------------------------|-------------------------|-------------------|
|LCD back cover (lid)              |Magnesium-aluminum alloy |6-39-NS501-022  or 6-39-NS511-020 |
|LCD front cover (bezel)           |Plastic                  |6-39-NS501-012     |
|Top case (palm rests/port covers) |Plastic                  |6-78-NS50PU02-010  |
|Bottom panel                      |Magnesium-aluminum alloy |6-39-NS503-014     |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

|Icon                                    |Color          |Description                                      |
|----------------------------------------|---------------|-------------------------------------------------|
|[Figure: Power LED | led-power.png]       |Orange         |Powered off, DC power plugged in                 |
|                                        |Green          |Powered on                                       |
|                                        |Blinking green |Sleeping (suspended)                             |
|[Figure: Battery LED | led-battery.png]   |Orange         |Battery charging                                 |
|                                        |Green          |Battery fully charged                            |

来源章节：External Overview / LED indicators

## exterior-keyboard-shortcuts：External Overview / Keyboard shortcuts

|Key                        |Shortcut|Action                             |
|---------------------------|--------|-----------------------------------|
|[Figure: Fn-F1 | fn-f1.png]  |Fn+F1   |Toggle trackpad                    |
|[Figure: Fn-F2 | fn-f2.png]  |Fn+F2   |Mic mute                           |
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
|[Figure: Fn-/ | fn-slash.png]|Fn+/    |Cycle keyboard brightness levels   |
|[Figure: Fn-- | fn-minus.png]|Fn+-    |Decrease keyboard brightness       |
|[Figure: Fn-+ | fn-plus.png] |Fn++    |Increase keyboard brightness       |
|1                          |Fn+1    |Toggle fan between max/automatic   |

来源章节：External Overview / Keyboard shortcuts

## interior-bottom-components-and-connectors：Internal Overview / Bottom components and connectors

[Figure: Internal Components | components-highlighted.webp]

- RAM slots are highlighted in purple
- Wireless card is highlighted in yellow
- Fan connector is highlighted in red
- Webcam/microphone connector is highlighted in orange
- M.2 SSDs are highlighted in dark green
    - Both slots support PCIe NVMe Gen 4
- CMOS battery connector is highlighted in pink
- Main battery connector is highlighted in cyan
- LCD panel connector is highlighted in light green
- Speaker connectors are highlighted in brown
- Touchpad connector is highlighted in white
- Lid switch/sensor connector is highlighted in olive
- BIOS flash chip (U22) is highlighted in black

来源章节：Internal Overview / Bottom components and connectors

## interior-under-keyboard-components-and-connectors：Internal Overview / Under-keyboard components and connectors

[Figure: Under-Keyboard Connectors | under-keyboard.webp]

- Keyboard connector is highlighted in red
- Keyboard backlight connector is highlighted in yellow
- Lid switch/sensor to motherboard connector is highlighted in cyan

来源章节：Internal Overview / Under-keyboard components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Darter Pro can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 7 minutes
**Difficulty:** Easy ●

### Steps to remove the bottom cover:

1. Place the machine lid-side down.
    - Use a soft surface (such as a towel) to avoid scratches.
2. Remove the 16 bottom panel screws:
    - 12 perimeter screws (thin), highlighted green below.
    - 2 keyboard screws (thick), highlighted yellow.
    - 2 inner hinge screws (thick), highlighted red.

[Figure: Bottom panel screws | bottom-screws.webp]

3. Pull the bottom panel off, starting from the hinges in the back.

来源章节：Parts & Repairs / Removing the bottom cover

## repairs-replacing-the-ram：Parts & Repairs / Replacing the RAM

The Darter Pro 9 supports up to 96GB (2x48GB) of DDR5 SO-DIMMs running at 5200MHz. If you've purchased new RAM, need to replace your RAM, or are reseating your RAM, follow these steps.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Easy ●

### Steps to replace the RAM:

1. Follow the steps above to remove the bottom cover.
2. Press the small tabs on both sides of the RAM simultaneously. The RAM should spring up to an angle.

[Figure: RAM slots | ram-slots.webp]

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
    - SSD-1 is closest to the battery (pictured on the left.)
    - SSD-2 is closest to the outer edge of the machine (on the right.)

[Figure: M.2 slots | m2-slots.webp]

3. Remove the existing M.2 drive by pulling it out of the slot.
4. Insert the new M.2 drive into the slot and hold it in place.
5. Replace the retainer screw.
6. If a square thermal pad was present on the old drive, transfer it to the new drive.

来源章节：Parts & Repairs / Replacing an M.2/NVMe SSD

## repairs-replacing-the-wireless-card：Parts & Repairs / Replacing the wireless card

Your Darter Pro's WiFi and Bluetooth are both handled by the same module. It is a standard M.2 2230 slot with PCIe and USB interfaces (E-key).

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
**Time estimate:** 12 minutes
**Difficulty:** Easy ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover.
2. Unplug the white connector attaching the CMOS battery to the motherboard (highlighted green below.)

[Figure: CMOS battery | cmos-battery.webp]

3. If you are replacing the CMOS battery, remove SSD-1 to expose the CMOS battery, then pull it away from the adhesive holding it to the case.
4. To clear the CMOS, disconnect the main battery, open the lid of the machine, and hold down the power button for at least 15 seconds to discharge any residual energy in the system.
5. Re-connect the CMOS battery and the main battery, and replace the SSD and bottom panel.
7. Power up the machine. The system may power itself off and on after initial boot; this is normal behavior when the CMOS has been reset.

来源章节：Parts & Repairs / Replacing the CMOS battery

## repairs-replacing-the-cooling-system：Parts & Repairs / Replacing the cooling system

The Darter Pro 9 has a single fan and heatsink to cool the CPU. The fan and heatsink are a single part (they should not be replaced separately from one another.)

If the fan becomes noisy and cleaning it out doesn't fix the issue, you may need a new fan. Contact support to start a warranty claim or parts purchase.

Depending on your climate and the age of the machine, it may be necessary to apply new thermal paste between the CPU and the heatsink. Thermal paste helps facilitate effective heat transfer between the CPU and the cooling equipment. These instructions can also be used in the unlikely event your heatsink needs to be replaced.

**Tools required:** Cross-head (Phillips) screwdriver, thermal paste
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the fan/heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Peel back the black tape holding the fan to the chassis, and remove any clear tape that is securing the wires.
3. Unplug the black fan connector.
    - This can alternatively be done after the fan/heatsink is removed.
4. Remove the seven heatsink/fan screws in order, starting with #1, then #2, and so on until #7.

[Figure: Thermal screws | thermal-system.webp]

5. Remove the fan and heatsink from the system, being careful not to bend the heatsink pipe. It may take some pressure to break the seal of the thermal paste.
6. Using a paper towel, remove the existing thermal paste. You may also use a small amount of rubbing alcohol if the old paste is dried or difficult to remove.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

7. After cleaning the CPU and heatsink, apply a small line of thermal paste directly onto the CPU chip.

[Figure: Thermal paste application | thermal-paste-application.webp]

8. Carefully replace the heatsink.
9. Replace the seven heatsink/fan screws in order, starting with #1, then #2, and so on until #7.

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be replaced individually.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the speakers:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the speakers from the motherboard.
    - This can alternatively be done after freeing the speakers.
    - The right speaker connector has a white receptacle, while the left speaker connector has a black receptacle.

[Figure: Speaker wires and posts | speakers.webp]

3. Pull the speakers up and off of the plastic posts.
    - When lifting the left speaker, free the wire from the channel in the chassis.
5. Slide the new speakers onto the plastic posts and connect them to the motherboard.
    - The left speaker's wire can be run through the channel in the chassis before or after plugging it in.

来源章节：Parts & Repairs / Replacing the speakers

## repairs-replacing-the-keyboard：Parts & Repairs / Replacing the keyboard

The keyboard can be replaced if its switches or electronics have been damaged.

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

## repairs-replacing-the-battery：Parts & Repairs / Replacing the battery

The battery provides primary power whenever the system is unplugged.

**CAUTION:** Do not use metal tools to remove the battery. Do not touch the battery contacts with your hands or metal objects. If the battery is damaged during repairs, do not reinsert it into the laptop. Do not continue to use the computer if it contains a damaged battery; it is a potential fire hazard to operate a computer with a damaged battery. Keep children away from, and properly dispose of, the damaged battery.

This product's battery is potentially recyclable. At the end of its useful life, under various state and local laws, it may be illegal to dispose of this battery into the municipal waste stream. Batteries may explode or leak if exposed to fire, or if improperly handled or discarded. Check with your local solid waste officials for details in your area for recycling options or proper disposal. Always dispose of batteries carefully, by placing a piece of tape on the contact points.

The model number for the Darter Pro 9's battery is `L140BAT-4`, and the original part number is `6-87-L140S-72B02`. Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number. You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 30 minutes
**Difficulty:** High ●

### Steps to replace the battery:

1. Follow the steps above to remove the bottom cover.
2. Unplug the white connector that connects the battery to the motherboard.

[Figure: Main battery | battery.webp]

3. Remove the battery.
    - The battery is secured to foam standoffs with double-sided tape.
    - A flat plastic object (such as a credit card) can be used to separate the tape from the battery.
    - If any tabs from the tape are visible around the perimeter of the battery, do not push them back under the battery.
        - Hold the tabs down while inserting the plastic tool to separate the tape and foam from the battery.
        - Alternatively, pull the tabs up with the battery to separate the tape and foam from the chassis.
4. Put the battery back (or put your new battery in its place) and plug it back into the motherboard.

来源章节：Parts & Repairs / Replacing the battery
