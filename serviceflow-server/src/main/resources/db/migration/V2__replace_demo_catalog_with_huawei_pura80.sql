-- Real product facts verified against Huawei China official pages on 2026-08-29.
-- list_price stores the official starting-price snapshot, not a live transaction price.

UPDATE product
SET sku = 'HUAWEI-PURA80-12-256',
    name = 'HUAWEI Pura 80',
    brand = 'HUAWEI',
    model = 'Pura 80',
    category = 'phone',
    specs = JSON_OBJECT(
        'screen_size', '6.6 英寸',
        'screen_type', 'OLED，1-120 Hz LTPO 自适应刷新率',
        'resolution', '2760 × 1256',
        'memory', '12 GB',
        'storage', '256 GB',
        'battery', '5600 mAh',
        'weight', '约 211 g',
        'dimensions', '157.7 × 74.4 × 8.2 mm',
        'wired_charging', '66 W',
        'wireless_charging', '50 W',
        'os', 'HarmonyOS 5.1',
        'camera_summary', '5000 万像素主摄 + 1200 万像素潜望式长焦 + 1300 万像素超广角',
        'source_url', 'https://consumer.huawei.com/cn/phones/pura80/specs/',
        'price_source_url', 'https://consumer.huawei.com/cn/phones/pura80/',
        'source_checked_at', '2026-08-29',
        'price_note', '华为中国官网起售价快照'
    ),
    list_price = 4699.00,
    sale_status = 'ON_SALE'
WHERE id = 1;

UPDATE product
SET sku = 'HUAWEI-PURA80PRO-12-256',
    name = 'HUAWEI Pura 80 Pro',
    brand = 'HUAWEI',
    model = 'Pura 80 Pro',
    category = 'phone',
    specs = JSON_OBJECT(
        'screen_size', '6.8 英寸',
        'screen_type', 'OLED，1-120 Hz LTPO 自适应刷新率',
        'resolution', '2848 × 1276',
        'memory', '12 GB',
        'storage', '256 GB',
        'battery', '5700 mAh',
        'weight', '约 219 g',
        'dimensions', '163 × 76.1 × 8.3 mm',
        'wired_charging', '100 W',
        'wireless_charging', '80 W',
        'os', 'HarmonyOS 5.1',
        'dust_water_resistance', 'IP68（2 米）/ IP69；防护能力非永久，浸液损坏不在保修范围',
        'camera_summary', '5000 万像素一英寸主摄 + 4800 万像素微距长焦 + 4000 万像素超广角',
        'source_url', 'https://consumer.huawei.com/cn/phones/pura80-pro/specs/',
        'price_source_url', 'https://consumer.huawei.com/cn/phones/pura80-pro/',
        'source_checked_at', '2026-08-29',
        'price_note', '华为中国官网起售价快照'
    ),
    list_price = 6499.00,
    sale_status = 'ON_SALE'
WHERE id = 2;

UPDATE product
SET sku = 'HUAWEI-PURA80ULTRA-16-512',
    name = 'HUAWEI Pura 80 Ultra',
    brand = 'HUAWEI',
    model = 'Pura 80 Ultra',
    category = 'phone',
    specs = JSON_OBJECT(
        'screen_size', '6.8 英寸',
        'screen_type', 'OLED，1-120 Hz LTPO 自适应刷新率',
        'resolution', '2848 × 1276',
        'memory', '16 GB',
        'storage', '512 GB',
        'battery', '5700 mAh',
        'weight', '约 233.5 g',
        'dimensions', '163 × 76.1 × 8.3 mm',
        'wired_charging', '100 W',
        'wireless_charging', '80 W',
        'os', 'HarmonyOS 5.1',
        'dust_water_resistance', 'IP68（2 米）/ IP69；防护能力非永久，浸液损坏不在保修范围',
        'camera_summary', '5000 万像素一英寸主摄 + 5000 万像素长焦 + 1250 万像素超长焦 + 4000 万像素超广角',
        'source_url', 'https://consumer.huawei.com/cn/phones/pura80-ultra/specs/',
        'price_source_url', 'https://consumer.huawei.com/cn/phones/pura80-ultra/',
        'source_checked_at', '2026-08-29',
        'price_note', '华为中国官网起售价快照'
    ),
    list_price = 9999.00,
    sale_status = 'ON_SALE'
WHERE id = 3;

-- Keep the seeded order history internally consistent with the replaced products.
UPDATE order_item SET unit_price = 4699.00 WHERE order_id = 1 AND product_id = 1;
UPDATE order_item SET unit_price = 6499.00 WHERE order_id = 2 AND product_id = 2;
UPDATE customer_order SET total_amount = 4699.00 WHERE id = 1;
UPDATE customer_order SET total_amount = 6499.00 WHERE id = 2;
