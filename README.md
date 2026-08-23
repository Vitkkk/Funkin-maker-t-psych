# FMM → Psych Converter (Android)

Aplicativo Android offline para converter exports `.FNMM` / `.FMM` do Funky Maker Mobile em mods da Psych Engine.

## v0.1

- seletor de arquivo pelo Android Storage Access Framework;
- conversão de chart, sustains e eventos;
- personagens: horizontal PNG strips → Sparrow atlas PNG/XML + Character JSON;
- ícones e áudio preservados sem recompressão;
- stage, câmeras, overlays e eventos recriados em Lua;
- formato de chart legado `{"song": {...}}`, compatível com forks Psych Android/0.7.x e carregável em builds 1.x;
- lane remap automático para preservar player/opponent sob a semântica legacy de `mustHitSection`;
- ZIP final pronto para extrair em `mods/`;
- processamento local/offline.

A conversão Android usa a mesma estratégia que corrigiu o erro `Invalid field: gfVersion` no teste do mod Huggy 2K26.

## Build

O GitHub Actions compila o APK automaticamente a cada push em `main`. O artifact se chama `FMM2Psych-APK`.
