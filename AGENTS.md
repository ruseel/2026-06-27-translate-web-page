# Translate WebPage

## Viewer / 배포

로컬 개발: `bb viewer 8000` → http://localhost:8000

배포 후에는 아래 도메인으로 접속할 수 있다.

- https://good-writes.rsl.kr

systemd 서비스 `srv.service` 가 포트 8000 에서 뷰어를 실행한다.

```bash
sudo systemctl restart srv   # 재시작
systemctl status srv          # 상태 확인
journalctl -u srv -f          # 로그 확인
```

## 워크플로

```bash
bb page <url>            # fetch + translate + genhtml (기본 ledger: translate)
bb fetch <url> [ledger]
bb translate <slug>
bb genhtml <slug>
bb viewer [port]
bb status <ledger> <slug>
```
