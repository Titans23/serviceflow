# System76 Lemur Pro (lemp12) — Technical Manual

System76 and contributors · CC BY-SA 4.0. Searchable English edition. Headings are indexed, figures are named in the text, and the source archive preserves the original layout.

来源 https://system76.com/tech-docs/models/lemp12/specs/
来源 https://system76.com/tech-docs/models/lemp12/external-overview/
来源 https://system76.com/tech-docs/models/lemp12/internal-overview/
来源 https://system76.com/tech-docs/models/lemp12/repairs/

## specs-introduction：Specifications / Introduction

[Figure: Lemur Pro | lemp12.webp]

The System76 Lemur Pro is a laptop with the following specifications:

来源章节：Specifications / Introduction

## specs-cpu：Specifications / CPU

- Supports Intel 13th Generation (Raptor Lake) CPUs
    - [Core i7-1355U](https://ark.intel.com/content/www/us/en/ark/products/232160/intel-core-i71355u-processor-12m-cache-up-to-5-00-ghz.html)
    - [Core i5-1335U](https://ark.intel.com/content/www/us/en/ark/products/232153/intel-core-i51335u-processor-12m-cache-up-to-4-60-ghz.html)

来源章节：Specifications / CPU

## specs-bios：Specifications / BIOS

- Flash chip options:
    - GigaDevice GD25B256EYIG
    - Macronix MX25L25673G
    - ...or other equivalent
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
- eDP display: 14.0" 1920x1080@60Hz LCD
    - LCD panel: Innolux N140HCE-EN2 (or equivalent)
- External video outputs:
    - 1x HDMI 2.1
    - 1x DisplayPort 1.4 over USB-C

来源章节：Specifications / Graphics

## specs-memory：Specifications / Memory

- Channel 0: 8-GB on-board DDR5
    - 4x [Samsung K4RAH165VB-BCQK](https://semiconductor.samsung.com/dram/ddr/ddr5/k4rah165vb-bcqk/) (2GB each)
- Channel 1: 8-GB/16-GB/32-GB/48-GB DDR5 SO-DIMM @ 4800 MHz

来源章节：Specifications / Memory

## specs-networking：Specifications / Networking

- M.2 PCIe/CNVi WiFi/Bluetooth
    - Intel Wi-Fi 6E AX210/AX211
        - Bluetooth 5.3

来源章节：Specifications / Networking

## specs-power：Specifications / Power

- 19V, 3.42A (65W) DC-in port
    - Barrel size: 5.5mm (outer), 2.5mm (inner)
- Included AC adapter: AcBel ADA012
    - AC power cord type: IEC C7 (non-polarized)
- USB-C charging compatible with 65W+ charger
- 73Wh 4-cell Lithium-Ion battery
    - Model number: L140BAT-4

来源章节：Specifications / Power

## specs-sound：Specifications / Sound

- Realtek ALC256-CGT audio chipset
- Internal speakers & microphone
- 3.5mm headphone/microphone combo jack
- HDMI, USB-C DisplayPort audio

来源章节：Specifications / Sound

## specs-storage：Specifications / Storage

- 1x M.2 (PCIe NVMe Gen 4)
- 1x M.2 (PCIe NVMe Gen 3 or SATA III)
- MicroSD card reader (RTS5227S)

来源章节：Specifications / Storage

## specs-usb：Specifications / USB

- 1x USB Type-C with Thunderbolt 4
    - Supports DisplayPort over USB-C
    - Supports USB-C charging (USB-PD)
- 1x USB 3.2 Gen 2 Type-A
- 1x USB 3.2 Gen 1 Type-A

来源章节：Specifications / USB

## specs-dimensions：Specifications / Dimensions

- 1.65cm x 32.2cm x 21.68cm, 1.15kg

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

[Figure: Back Ports | ports-back.png]

来源章节：External Overview / Back side overview

## exterior-keyboard-touchpad-overview：External Overview / Keyboard & touchpad overview

[Figure: Keyboard & Touchpad | keyboard-touchpad.webp]

来源章节：External Overview / Keyboard & touchpad overview

## exterior-speakers：External Overview / Speakers

The machine has two bottom-firing speakers near the front, angled outward.

[Figure: Bottom Ports | ports-bottom.webp]

来源章节：External Overview / Speakers

## exterior-materials：External Overview / Materials

|Part                              |Material                 |Part Number        |
|----------------------------------|-------------------------|-------------------|
|LCD back cover (lid)              |Magnesium-aluminum alloy |6-39-L1401-025     |
|LCD front cover (bezel)           |Plastic                  |6-40-L1401-042     |
|Top case (palm rests/port covers) |Magnesium-aluminum alloy |6-L140AU-KB-WBL-US |
|Bottom panel                      |Magnesium-aluminum alloy |6-39-L14P3-011     |

来源章节：External Overview / Materials

## exterior-led-indicators：External Overview / LED indicators

There is an LED light next to the DC-in connector (charger port.) The color and pattern indicate:

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

来源章节：External Overview / Keyboard Shortcuts

## interior-components-and-connectors：Internal Overview / Components and connectors

Components can be replaced using the instructions under Parts & Repairs. Connectors can be reseated for troubleshooting purposes.

[Figure: Internal Components | components-highlighted.webp]

- RAM slot is highlighted in maroon
- M.2 SSDs are highlighted in green
    - Slot closest to RAM supports PCIe NVMe Gen 4
    - Slot closest to battery supports PCIe NVMe Gen 3 and SATA III
- Wireless card is highlighted in navy
- LCD cable connector is highlighted in yellow
- Webcam/microphone connector is highlighted in cyan
- Touchpad connector is highlighted in orange
- Keyboard connector (main) is highlighted in olive
- Keyboard backlight connector is highlighted in white
- CMOS battery connector is highlighted in brown
- Battery connector is highlighted in light red
- Speaker connectors are highlighted in pink
- BIOS flash chip (U41) is highlighted in black

来源章节：Internal Overview / Components and connectors

## repairs-introduction：Parts & Repairs / Introduction

Many components in your Lemur Pro can be upgraded or replaced as necessary. Follow these step-by-step guides for instructions.

来源章节：Parts & Repairs / Introduction

## repairs-removing-the-bottom-cover：Parts & Repairs / Removing the bottom cover

Removing the cover is required to access the internal components. Prior to removing the cover, ensure the AC power is unplugged and all peripherals (including SD cards and USB drives) are unplugged or removed from the system.

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

The Lemur Pro 12 comes with 8GB of 4800 MHz RAM soldered onto the motherboard, which cannot be replaced. There is an additional RAM slot, which can support up to a 48GB stick for 56GB of RAM total. The additional stick is a standard DDR5 SO-DIMM running at 4800 MHz.

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

This model supports two M.2 SSDs. Both slots are size 2280. SSD-1 supports PCIe NVMe Generation 3 or SATA III, while SSD-2 supports PCIe NVMe Generation 4.

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

## repairs-replacing-the-cmos-battery：Parts & Repairs / Replacing the CMOS battery

The CMOS battery supplies power to the system's CMOS chip. UEFI settings and the computer's hardware clock are stored on the CMOS. If your system doesn't boot, you can reset the CMOS to force a low-level hardware reset. If your clock is constantly resetting, it's likely your CMOS battery needs to be replaced.

**Warning (ingestion hazard):** Keep batteries out of reach of children. Death or serious injury can occur if ingested. If a battery is suspected to be swallowed or inserted inside any part of the body, seek immediate medical attention. In the US, you can also call the National Battery Ingestion Hotline for guidance: [1 (800) 498-8666](tel:18004988666)

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the CMOS battery:

1. Follow the steps above to remove the bottom cover.
2. Follow the steps above to remove the primary SSD.
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

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 20 minutes
**Difficulty:** High ●

### Steps to replace the fans/heatsink/thermal paste:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the small connector plugging the fan into the motherboard, highlighted yellow below.
    - This can alternatively be done after the fan/heatsink is removed.

[Figure: Heatsink/fan screws | thermal-system.webp]

4. Remove the two fan screws (highlighted cyan above) and any clear tape holding the fan in place.
5. Remove the heatsink screws in reverse order, starting with #3, then #2, and finally #1.
6. Remove the heatsink/fan from the system, being careful not to bend the heatsink pipe. It may take some pressure to break the seal of the thermal paste.
7. Using a paper towel, remove the existing thermal paste. You may also use a small amount of rubbing alcohol to remove excess or difficult-to-remove paste.

[Figure: Thermal paste removal | thermal-paste-removal.webp]

8. After cleaning the CPU and heatsink, apply a small line of thermal paste directly onto the CPU.

[Figure: Thermal paste application | thermal-paste-application.webp]

9. Carefully replace the fan and heatsink.
10. Replace the heatsink screws, starting with #1, then #2, and finally #3.
11. Replace the fan screws and plug the fan back in.
    - When plugging the fan in, the black wire goes on the left (closest to the side of the machine.)

来源章节：Parts & Repairs / Replacing the cooling system

## repairs-replacing-the-speakers：Parts & Repairs / Replacing the speakers

The system has two bottom-firing speakers, which can be removed and replaced individually.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 10 minutes
**Difficulty:** Medium ●

### Steps to replace the speakers:

1. Follow the steps above to remove the bottom cover.
2. Disconnect the speaker wire from the motherboard.
    - This can alternatively be done after freeing the speaker.

[Figure: Speaker wires and tabs | speakers.webp]

3. Pull the tops of the purple rubber circles over the black tabs holding them in place.
4. Lift the speaker up and off of the plastic posts.

[Figure: Speaker removal | speaker-removal.webp]

5. Slide the new speaker onto the plastic posts and connect it to the motherboard.

来源章节：Parts & Repairs / Replacing the speakers

## repairs-replacing-the-main-battery：Parts & Repairs / Replacing the main battery

The battery provides primary power whenever the system is unplugged.

The model number for the Lemur Pro 12's battery is `L140BAT-4`, and the original part number is `6-87-L140S-72B02`. Third-party battery sellers may list one or both of these numbers, and may offer other compatible part numbers with the same model number. You can also [contact System76](https://support.system76.com) to purchase a replacement battery.

**CAUTION:** Do not use metal tools to remove the battery. Do not touch the battery contacts with your hands or metal objects. If the battery is damaged during repairs, do not reinsert it into the laptop. Do not continue to use the computer if it contains a damaged battery. It is a potential fire hazard to operate a computer with a damaged battery. Keep children away from, and properly dispose of, the damaged battery. Always dispose of batteries carefully, by placing a piece of tape on the contact points. Take damaged batteries to an electronics recycling center and contact System76 to purchase a replacement. Batteries may explode or leak if exposed to fire, or if improperly handled or discarded. Only use batteries designed for your specific computer. This product contains a rechargeable battery. The battery is recyclable. At the end of its useful life, under various state and local laws, it may be illegal to dispose of this battery into the municipal waste stream. Check with your local solid waste officials for details in your area for recycling options or proper disposal.

**Tools required:** Cross-head (Phillips) screwdriver
**Time estimate:** 30 minutes
**Difficulty:** High ●

### Steps to replace the main battery:

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

来源章节：Parts & Repairs / Replacing the main battery
