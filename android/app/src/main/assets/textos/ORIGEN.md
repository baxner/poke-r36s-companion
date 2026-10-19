# Nombres en español

Tablas de texto de PKHeX (https://github.com/kwsch/PKHeX), commit
`6042efa241a9925ce57e645329ea7f976deabdd3`, carpeta `PKHeX.Core/Resources/text/`, licencia
GPL-3.0 (la misma que el motor @pkhex-everywhere/engine que lleva la app). El motor solo da
nombres en inglés; la app traduce por número con estas tablas. La línea N del archivo es el id
N-1 (la primera línea es el id 0).

Se usa el español de los juegos (`es`), no el latinoamericano de los juegos nuevos (`es-419`):
las partidas de GBA del usuario están en español y muestran esos nombres ("Placaje", no
"Tacleada"; "Danza Espada", no "Danza de Espadas"). Especies, objetos y lugares son iguales en
las dos; cambian 263 movimientos y 51 habilidades.

| Archivo | Origen | Numeración |
|---|---|---|
| `es/especies.txt` | `other/es/text_Species_es.txt` | Pokédex Nacional |
| `es/movimientos.txt` | `other/es/text_Moves_es.txt` | id de movimiento de PKHeX |
| `es/habilidades.txt` | `other/es/text_Abilities_es.txt` | id de habilidad de PKHeX |
| `es/naturalezas.txt` | `other/es/text_Natures_es.txt` | 0 (Fuerte) a 24 |
| `es/tipos.txt` | `other/es/text_Types_es.txt` | id de tipo de PKHeX |
| `es/juegos.txt` | `other/es/text_Games_es.txt` | `GameVersion` de PKHeX |
| `es/objetos.txt` | `items/text_Items_es.txt` | 4.ª generación en adelante (`catalog.names`) |
| `es/objetos-gen3.txt` | `items/gen3/text_ItemsG3_es.txt` | 3.ª generación: lo que usan las partidas de GBA (`heldItem`, `inventory`) |
| `es/lugares-gen3.txt` | `locations/gen3/text_rsefrlg_00000_es.txt` | lugar de captura en Rubí, Zafiro, Esmeralda, Rojo Fuego y Verde Hoja |
