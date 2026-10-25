"""Generate additive fixtures for a fresh serviceflow_eval_* database; never connects to a DB."""
import argparse
from datetime import datetime
from pathlib import Path
import secrets
import uuid

from eval_v2 import ROOT, SUITE, read_json, validate_suite, write_json


def sql_string(value):
    return "'" + str(value).replace('\\', '\\\\').replace("'", "''") + "'"


def prepare(suite, run_id):
    if not run_id.isdigit() or len(run_id) != 18:
        raise ValueError('run ID must be an 18-digit timestamp/random suffix')
    # Existing public demo-password hash, only for explicitly synthetic evaluation accounts.
    password_hash = '$2b$12$LMp0kolR.Pc5i0IYnxagluMow7nG2rkQ/TSyyrxyPTOrXJcwKlC1G'
    sql = ["-- Synthetic, additive fixtures. Run only AFTER Flyway on an isolated database.",
           "CREATE TEMPORARY TABLE sf_eval_guard (allowed BOOLEAN CHECK (allowed = TRUE));",
           "INSERT INTO sf_eval_guard VALUES (LEFT(DATABASE(), CHAR_LENGTH('serviceflow_eval_')) = 'serviceflow_eval_');", 'START TRANSACTION;']
    receipt = {'synthetic': True, 'runId': run_id, 'requiredDatabasePrefix': 'serviceflow_eval_', 'principals': {}, 'cases': {}}
    for role in ('customer', 'other'):
        username = f'eval_{role}_{run_id}'
        email = username + '@example.invalid'
        receipt['principals'][role] = username
        sql.append(f"INSERT INTO customer(name,email) VALUES ({sql_string('合成评测客户 '+role)},{sql_string(email)});")
        sql.append(f"INSERT INTO app_user(username,password_hash,role,customer_id) VALUES ({sql_string(username)},{sql_string(password_hash)},'CUSTOMER',LAST_INSERT_ID());")
    ordinal = 0
    for case in suite['cases']:
        fixture = case.get('fixture')
        if not fixture:
            continue
        if fixture['orderStatus'] not in {'CREATED', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED'}:
            raise ValueError('Invalid fixture order status')
        if fixture['paymentStatus'] not in {'PENDING', 'PAID', 'FAILED'} or fixture['owner'] not in receipt['principals']:
            raise ValueError('Invalid fixture payment/owner')
        variables = {key: str(uuid.uuid5(uuid.NAMESPACE_URL, run_id + ':' + case['id'] + ':' + key))
                     for key in ('eventId', 'eventId2', 'eventId3')}
        for key in ['orderNo'] + (['secondOrderNo'] if fixture.get('secondOrder') else []):
            ordinal += 1
            order_no = 'SF' + run_id + f'{ordinal:03}'
            variables[key] = order_no
            customer = f"(SELECT customer_id FROM app_user WHERE username={sql_string(receipt['principals'][fixture['owner']])})"
            sql.append('INSERT INTO customer_order(order_no,customer_id,status,total_amount) VALUES '
                       f"({sql_string(order_no)},{customer},{sql_string(fixture['orderStatus'])},99.00);")
            sql.append('SET @sf_eval_order_id = LAST_INSERT_ID();')
            sql.append("INSERT INTO order_item(order_id,product_id,quantity,unit_price) SELECT @sf_eval_order_id,MIN(id),1,99.00 FROM product;")
            paid_at = 'CURRENT_TIMESTAMP(6)' if fixture['paymentStatus'] == 'PAID' else 'NULL'
            sql.append(f"INSERT INTO payment(order_id,status,refund_status,paid_at) VALUES (@sf_eval_order_id,{sql_string(fixture['paymentStatus'])},'NONE',{paid_at});")
            if fixture['orderStatus'] in {'SHIPPED', 'DELIVERED'}:
                sql.append("INSERT INTO shipment_event(order_id,description,location,occurred_at) VALUES (@sf_eval_order_id,'合成物流事件','评测分拨中心',CURRENT_TIMESTAMP(6));")
        receipt['cases'][case['id']] = variables
    sql.append('COMMIT;')
    return '\n'.join(sql) + '\n', receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', type=Path, default=SUITE)
    parser.add_argument('--output', type=Path, default=ROOT / 'quality/evaluation/reports')
    args = parser.parse_args()
    suite = read_json(args.suite)
    validate_suite(suite)
    run_id = datetime.now().strftime('%Y%m%d%H%M%S') + f'{secrets.randbelow(10000):04}'
    sql, receipt = prepare(suite, run_id)
    directory = args.output / ('fixtures-' + run_id)
    directory.mkdir(parents=True, exist_ok=False)
    (directory / 'fixtures.sql').write_text(sql, encoding='utf-8')
    write_json(directory / 'fixtures.json', receipt)
    print(f'Generated {len(receipt["cases"])} scenario fixtures at {directory}; no database was modified.')


if __name__ == '__main__':
    main()
