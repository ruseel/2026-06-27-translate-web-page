---
description: URL을 bb page 로 실행하고 그러면 번역을 pi 를 통해서 하고 side-by-side HTML을 생성합니다.
argument-hint: "<URL>"
---

`/translate`에 전달된 URL: `$1`

**`bb page` 명령을 사용해 번역하세요. 

## 실행 규칙

1. `$1`이 비어 있으면 작업을 멈추고 URL을 요청하세요.
2. 프로젝트 루트에서 다음 명령을 실행하세요.

```bash
bb page "$1"
```

## 파이프라인이 생성하는 산출물

`bb page`는 내부적으로 `fetch -> translate -> genhtml`을 실행하고, `out/` 아래에 다음 파일을 생성합니다.

- `out/<slug>.defuddle.json`
- `out/<slug>.jsonld`
- `out/<slug>.translations.jsonld`
- `out/<slug>.translation-pairs.html`

## 완료 보고

HTML 경로를 알려주세요. 

```
번역이 완료되었습니다. 

file://...url for html...
```
