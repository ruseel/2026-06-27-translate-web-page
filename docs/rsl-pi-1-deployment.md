# rsl-pi-1 deployment notes

Current deployment target:

- VM: `rsl-pi-1.exe.xyz`
- Project directory: `/home/exedev/2026-06-27-translate-web-page`
- Public viewer domain: `good-writes.rsl.kr`
- Fluree domain: `fluree.rsl.kr`

## Network / proxy layout

```text
Internet HTTPS
  -> exe.dev custom-domain proxy for rsl-pi-1.exe.xyz
  -> VM port 8080
  -> nginx host routing
       Host: good-writes.rsl.kr -> 127.0.0.1:8000  (bb viewer)
       Host: fluree.rsl.kr      -> 127.0.0.1:8090  (Fluree)
```

Internal ports:

| Port | Process | Purpose |
| --- | --- | --- |
| `8000` | `bb viewer 8000` / `srv.service` | Translate WebPage viewer |
| `8080` | nginx | Host-based reverse proxy entrypoint for exe.dev |
| `8090` | Fluree server / `fluree.service` | Fluree HTTP API |

## DNS settings

Both custom domains should point at the exe.dev VM hostname:

```text
good-writes.rsl.kr  CNAME  rsl-pi-1.exe.xyz
fluree.rsl.kr       CNAME  rsl-pi-1.exe.xyz
```

If the DNS provider is Cloudflare, use **DNS only** unless Cloudflare snippets/workers are intentionally configured. exe.dev custom domains expect the hostname to resolve to the VM.

## exe.dev proxy / custom-domain setup

Run from a local machine that has `ssh exe.dev` access:

```bash
ssh exe.dev domain add rsl-pi-1 good-writes.rsl.kr
ssh exe.dev domain add rsl-pi-1 fluree.rsl.kr
ssh exe.dev share port rsl-pi-1 8080
```

Choose visibility:

```bash
# Public web access
ssh exe.dev share set-public rsl-pi-1

# Or private exe.dev-authenticated access
ssh exe.dev share set-private rsl-pi-1
```

Useful checks:

```bash
ssh exe.dev domain ls rsl-pi-1
ssh exe.dev share show rsl-pi-1
```

## nginx setup

Config file:

```text
/etc/nginx/sites-available/rsl-sites
/etc/nginx/sites-enabled/rsl-sites -> /etc/nginx/sites-available/rsl-sites
```

The default nginx site was disabled because it attempted to bind port `8000`, which conflicts with the viewer.

Important Fluree proxy settings:

- preserve `Authorization` header for Bearer tokens
- disable buffering for Fluree pack/pull streaming and SSE
- allow large bodies for import/push

Manage nginx:

```bash
sudo nginx -t
sudo systemctl status nginx
sudo systemctl reload nginx
sudo systemctl restart nginx
```

Local host-routing tests from the VM:

```bash
curl -H 'Host: good-writes.rsl.kr' http://127.0.0.1:8080/
curl -H 'Host: fluree.rsl.kr' http://127.0.0.1:8080/health
```

## Viewer service

Project-local service file copy:

```text
/home/exedev/2026-06-27-translate-web-page/srv.service
```

Expected installed systemd unit:

```text
/etc/systemd/system/srv.service
```

Manage viewer:

```bash
sudo systemctl status srv
sudo systemctl restart srv
journalctl -u srv -f
```

## Fluree server

Binary:

```text
/home/exedev/bin/fluree
```

Systemd unit:

```text
/etc/systemd/system/fluree.service
```

The unit intentionally uses `WorkingDirectory=/home/exedev`, while `--storage-path` points at this project's storage. This prevents the project CLI/viewer from seeing `.fluree/server.meta.json` and auto-routing its internal `fluree query` calls through the authenticated HTTP server.

Fluree listens on:

```text
0.0.0.0:8090
```

### Fluree storage location

Important: the server uses the project Fluree storage, not `/var/lib/fluree`:

```text
/home/exedev/2026-06-27-translate-web-page/.fluree/storage
```

This contains the existing ledgers used by the viewer, including:

- `translate:main`
- `translate-small:main`
- `__nope__:main`

Check local ledgers directly:

```bash
cd /home/exedev/2026-06-27-translate-web-page
/home/exedev/bin/fluree list --direct
```

Check ledgers through the Fluree HTTP API:

```bash
TOKEN=$(cat /home/exedev/.fluree/admin.jwt)
curl -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:8090/v1/fluree/ledgers
```

Through nginx host routing:

```bash
TOKEN=$(cat /home/exedev/.fluree/admin.jwt)
curl -H 'Host: fluree.rsl.kr' \
  -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:8080/v1/fluree/ledgers
```

Manage Fluree:

```bash
sudo systemctl status fluree
sudo systemctl restart fluree
journalctl -u fluree -f
```

## Fluree auth / admin token

Current trusted issuer DID:

```text
did:key:z6MkfSG31DRdKMTicjiggLMnGu5JNktrngG2bq3XYKhgMXqF
```

Key/token files on the VM:

```text
/home/exedev/.fluree/admin.jwt          # Bearer token for clients
/home/exedev/.fluree/admin.key          # Ed25519 private signing key; keep secret
/home/exedev/.fluree/admin-keypair.json # keypair metadata; keep secret
```

The server trusts this DID for:

- admin endpoints
- data API
- storage proxy / replication endpoints

The admin token was minted with `--all`, so it supports clone/fetch/pull/push as well as read/write.

Download the token to a laptop:

```bash
scp rsl-pi-1.exe.xyz:/home/exedev/.fluree/admin.jwt ./rsl-pi-1-fluree-admin.jwt
chmod 600 ./rsl-pi-1-fluree-admin.jwt
```

If SSH needs an explicit user:

```bash
scp exedev@rsl-pi-1.exe.xyz:/home/exedev/.fluree/admin.jwt ./rsl-pi-1-fluree-admin.jwt
chmod 600 ./rsl-pi-1-fluree-admin.jwt
```

Only download the private key if you need to mint new tokens locally:

```bash
scp rsl-pi-1.exe.xyz:/home/exedev/.fluree/admin.key ./rsl-pi-1-fluree-admin.key
chmod 600 ./rsl-pi-1-fluree-admin.key
```

## Client usage from laptop

After DNS and exe.dev domain setup are complete:

```bash
fluree remote add origin https://fluree.rsl.kr --token @./rsl-pi-1-fluree-admin.jwt
fluree auth status --remote origin
fluree list --remote origin
```

Clone/pull examples:

```bash
fluree clone origin translate
fluree pull translate
```

Direct API check:

```bash
curl -H "Authorization: Bearer $(cat ./rsl-pi-1-fluree-admin.jwt)" \
  https://fluree.rsl.kr/v1/fluree/ledgers
```

If `fluree.rsl.kr` is not ready yet, use SSH tunneling:

```bash
ssh -L 8090:localhost:8090 rsl-pi-1.exe.xyz
fluree remote add origin http://localhost:8090 --token @./rsl-pi-1-fluree-admin.jwt
fluree list --remote origin
```
