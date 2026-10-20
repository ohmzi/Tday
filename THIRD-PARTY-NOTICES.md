# Third-Party Notices

T'Day is distributed under the [T'Day Personal-Use Source-Available License](LICENSE).
It includes and builds on third-party components that carry their own licences. Those
licences govern those components; nothing in T'Day's own licence reduces the rights they
grant you.

This file covers components whose source is **vendored into this repository** or from which
parts of this repository derive. Package-manager dependencies, which are fetched at build
time rather than committed here, are declared in `tday-web/package.json` (with resolved
versions and integrity hashes in `tday-web/package-lock.json`) and in the `build.gradle.kts`
files of the Gradle modules; each is distributed under its own licence by its own authors.

---

## tatsu

Parts of the web client derive from **tatsu** (<https://github.com/ZhengJiawen44/tatsu>),
including early versions of some shared date, caret and form helpers.

```
MIT License

Copyright (c) 2026 Zheng Jiawen

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## shadcn/ui

The primitives under `tday-web/src/components/ui/` were generated from **shadcn/ui**
(<https://github.com/shadcn-ui/ui>) and are copied into this repository rather than
installed as a dependency, as that project intends.

```
MIT License

Copyright (c) 2023 shadcn

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Lucide

Icon artwork throughout the web, Android and iOS clients comes from **Lucide**
(<https://github.com/lucide-icons/lucide>), used on the web via `lucide-react` and
vendored as SVG assets on the native clients.

```
ISC License

Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of
Feather (MIT). All other copyright (c) for Lucide are held by Lucide Contributors
2022.

Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted, provided that the above copyright notice
and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.
```

---

## Nunito

The bundled UI typeface is **Nunito**, Copyright 2014 The Nunito Project Authors
(<https://github.com/googlefonts/nunito>), under the SIL Open Font License 1.1. The full
licence text ships with the font at `android-compose/app/src/main/res/raw/nunito_ofl.txt`.

---

If you believe a component is missing from this file, please open an issue.
