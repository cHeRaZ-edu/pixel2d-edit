# pixel2d-edit

Motor/editor 2D cenital para Android: diseñar niveles de exploración en el cel, con assets libres
o propios, y probarlos ahí mismo. Hobby, por partes, siempre con algo jugable al cerrar cada hito.

## APK

Cada push a `main` compila con GitHub Actions. El APK debug queda en
[Releases → latest](../../releases/tag/latest) (`pixel2d-edit-debug.apk`).

## Secciones del motor

- **A. Taller (núcleo):** lienzo pixel, paleta, espejo, frames, onion skin. Lo usan B, C y D.
- **B. Tiles:** tileset, preview 3×3, autotile, follaje.
- **C. Props:** plantillas de silueta (llave, frasco, poción, escudo) + datos mínimos (nombre, tipo).
- **D. Personaje:** maniquí base 4 direcciones con caminar; capas de equipo encima; izquierda = espejo de derecha.
- **E. Nivel:** capas, colisión, spawn, props colocados, puertas entre mapas.
- **F. Jugar:** player, cámara, recoger, cambiar de mapa.

## Hitos

- [x] **1.** Nivel con Tiny Town + cuadrado caminando con colisión (E y F mínimos)
- [ ] **2.** Taller básico + primeros tiles propios (espejo, preview 3×3)
- [ ] **3.** Dibujar una poción, colocarla y recogerla (C)
- [ ] **4.** Maniquí caminando, luego una capa de ropa (D)
- [ ] **5.** Autotile y follaje
- [ ] **6.** Puertas entre mapas, guardar/exportar (JSON compatible con Tiled)

Regla: una sección abierta a la vez; ningún hito se cierra sin algo jugable.

## Hito 1 — cómo se usa

- **✋ Mover / ✎ Pintar** (botón de arriba): en Mover un dedo arrastra el mapa y no pinta nada.
  Elegir un tile o una herramienta cambia solo a Pintar. En cualquier modo, 2 dedos mueven y hacen zoom.
- **Capas:** Suelo, Deco, Encima (se dibuja sobre el player) y Colisión (rojo = sólido).
- **Herramientas:** Lápiz, Borrar, Relleno, Gotero (toma el tile de una celda), Spawn (dónde aparece el player).
- **Packs:** Pueblo, Mazmorra y Granja (fila arriba de la paleta). Se mezclan en el mismo mapa.
- **Sellos de varios tiles:** en la paleta, mantén presionado y arrastra para elegir un rectángulo
  (árbol de 1×2, bosque 2×2, casa). Se pinta de un toque; al arrastrar se acomodan en rejilla y
  el Relleno repite el patrón.
- **Copa→Encima** (activo por defecto): pintando en Deco, las filas de arriba del sello van solas a
  Encima, para que el player pase por detrás de copas y techos. Borrar en Deco quita las dos.
- **▶ Jugar:** el cuadrado aparece en el spawn; toca y arrastra en cualquier parte para moverte.
- Se guarda solo al salir de la app (autosave).

## Créditos

Tilesets incluidos: **Tiny Town**, **Tiny Dungeon** y **Tiny Farm** de [Kenney](https://www.kenney.nl), CC0
(licencias en `app/src/main/assets/tilesets/*-LICENSE.txt`).
