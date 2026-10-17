#!/usr/bin/env python3
"""Black-box checks for the lab's documented incident behavior. JDK 17 required."""
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8080'


def request(path, data=None, headers=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(BASE + path, data=body,
        headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.loads(response.read())


def main():
    with socket.socket() as port:
        if port.connect_ex(('127.0.0.1', 8080)) == 0:
            raise RuntimeError('Port 8080 must be free before running tests')
    (ROOT / 'out').mkdir(exist_ok=True)
    subprocess.run(['javac', '-d', 'out',
        *map(str, (ROOT / 'src/main/java/lab/payments').glob('*.java'))],
        cwd=ROOT, check=True)
    with tempfile.TemporaryFile(mode='w+') as log:
        server = subprocess.Popen(['java', '-cp', 'out', 'lab.payments.PaymentSupportLab'],
            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(50):
                if server.poll() is not None:
                    raise RuntimeError('Lab server exited before readiness')
                try:
                    if request('/health')[0] == 200:
                        break
                except (OSError, urllib.error.URLError):
                    time.sleep(0.1)
            else:
                raise RuntimeError('Lab server did not become ready')
            payload = {'customerId': 'synthetic-customer', 'amount': 12.34, 'currency': 'BRL'}
            def create(bug=None, **changes):
                headers = {'X-Correlation-Id': 'scenario-' + (bug or 'normal')}
                if bug:
                    headers['X-Lab-Bug'] = bug
                return request('/payments', {**payload, **changes}, headers)

            assert create()[1]['status'] == 'SETTLED'
            assert create(paymentMethod='CREDIT_CARD')[1]['status'] == 'AUTHORIZED'
            expected = {'duplicate-charge', 'timeout-after-accept', 'stale-status',
                'rounding-error', 'missing-correlation', 'wrong-http-status',
                'null-response', 'retry-storm', 'duplicate-webhook', 'out-of-order-events'}
            assert set(request('/admin/scenarios')[1]) == expected
            status, duplicate = create('duplicate-charge', paymentMethod='CREDIT_CARD')
            assert status == 201 and duplicate['captures'] == 2
            pix = create('duplicate-charge')[1]
            assert any(e['type'] == 'PIX_SETTLED_DUPLICATE' for e in pix['events'])
            assert create('timeout-after-accept')[0] == 504
            assert any(p['correlationId'] == 'scenario-timeout-after-accept'
                for p in request('/payments')[1])
            stale = create('stale-status')[1]
            assert stale['status'] == 'PENDING' and any(e['type'] == 'PIX_SETTLED' for e in stale['events'])
            assert create(amount=1.005)[1]['amount'] == 1.01
            assert create('rounding-error', amount=1.005)[1]['amount'] == 1.0
            assert create('missing-correlation')[0] == 201
            log.flush()
            log.seek(0)
            logs = [json.loads(line) for line in log if line.strip()]
            assert any(row.get('event') == 'payment_created' and row.get('correlationId') == '' for row in logs)
            assert create(amount=-1)[0] == 400
            status, invalid = create('wrong-http-status', amount=-1)
            assert status == 200 and invalid['error'] == 'INVALID_REQUEST'
            status, empty = create('null-response')
            assert status == 502 and request('/payments/' + empty['paymentId'])[1]['status'] == 'UNKNOWN'
            storm = create('retry-storm', paymentMethod='CREDIT_CARD')[1]
            assert storm['attempts'] == 4 and storm['captures'] == 4
            webhook = create('duplicate-webhook')[1]
            assert sum(e['type'] == 'WEBHOOK_DELIVERED' for e in webhook['events']) == 2
            older = create('out-of-order-events')[1]
            assert older['status'] == 'PENDING' and older['events'][0]['type'] == 'PAYMENT_PENDING'
            headers = {'Idempotency-Key': 'scenario-replay'}
            first = request('/payments', payload, headers)[1]
            replay = request('/payments', payload, headers)
            assert replay[0] == 200 and replay[1]['paymentId'] == first['paymentId']
            assert request('/payments/not-found')[0] == 404
            assert request('/admin/reset', {})[1]['reset'] is True
            assert request('/payments')[1] == []
            print('All 10 incident scenarios and normal/replay/reset checks passed.')
        except BaseException:
            log.seek(0)
            print(log.read())
            raise
        finally:
            server.terminate()
            try:
                server.wait(timeout=5)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()


if __name__ == '__main__':
    main()
