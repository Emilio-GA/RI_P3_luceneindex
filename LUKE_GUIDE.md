# 🎯 Guía Completa de Luke para AirbnbIndexador

## 📋 Tabla de Contenidos
1. [Introducción](#introducción)
2. [Abrir Índices en Luke](#abrir-índices-en-luke)
3. [Interfaz de Luke](#interfaz-de-luke)
4. [Búsquedas en Lucene](#búsquedas-en-lucene)
5. [Ejemplos Prácticos para Airbnb](#ejemplos-prácticos-para-airbnb)
6. [Operaciones Comunes](#operaciones-comunes)
7. [Troubleshooting](#troubleshooting)

---

## 🚀 Introducción

**Luke (Lucene Index Toolbox)** es una herramienta GUI para inspeccionar, navegar y buscar en índices de Lucene. Es esencial para:
- Verificar que la indexación funcionó correctamente
- Explorar documentos y campos
- Probar búsquedas antes de implementarlas en código
- Debuggear problemas de indexación
- Analizar la estructura del índice

---

## 📂 Abrir Índices en Luke

### Método 1: Desde la Terminal (Recomendado)

```bash
cd /home/felipe/Documents/1ercuatri2025-26/RI/P3

# Abrir índice de propiedades
./lucene-10.3.1/bin/luke.sh "$(pwd)/index_root/index_properties" &

# Abrir índice de hosts (en otra ventana)
./lucene-10.3.1/bin/luke.sh "$(pwd)/index_root/index_hosts" &
```

### Método 2: Desde la GUI

1. Ejecuta: `./lucene-10.3.1/bin/luke.sh`
2. En el diálogo "Open Index":
   - Haz clic en "Browse"
   - Navega a: `index_root/index_properties/` o `index_root/index_hosts/`
   - Selecciona el directorio del índice (NO `index_root/` directamente)

**⚠️ Importante:** Luke abre **un índice a la vez**. Si quieres ver ambos, abre dos ventanas de Luke.

---

## 🖥️ Interfaz de Luke

### Pestañas Principales

#### 1. **Overview** (Vista General)
- **Número de documentos**: Total de propiedades/hosts indexados
- **Campos indexados**: Lista de todos los campos
- **Tamaño del índice**: Estadísticas de tamaño
- **Última actualización**: Información del commit

**Usa esto para:** Verificar que el índice tiene el número correcto de documentos

#### 2. **Documents** (Navegación de Documentos)
- **Navegar por documento**: Usa el slider o escribe el número de documento
- **Ver campos almacenados**: Todos los campos con `Field.Store.YES`
- **Ver términos**: Términos indexados para cada campo
- **Ver análisis**: Cómo se analizó el texto

**Usa esto para:**
- Verificar que los datos se guardaron correctamente
- Inspeccionar documentos individuales
- Ver cómo se tokenizó el texto

#### 3. **Search** (Búsqueda)
- **Query parser**: Escribe consultas en sintaxis Lucene
- **Default field**: Campo por defecto (deja vacío para buscar en todos)
- **Resultados**: Muestra documentos encontrados con scores

**Usa esto para:** Probar búsquedas antes de implementarlas en código

#### 4. **Commits** (Información de Commits)
- Historial de commits
- Información de segmentos

#### 5. **Plugins** (Extensiones)
- Herramientas adicionales disponibles

---

## 🔍 Búsquedas en Lucene

### Sintaxis Básica

#### Búsqueda Simple
```
beach
```
Busca "beach" en todos los campos.

#### Búsqueda por Campo
```
campo:valor
```
Busca "valor" solo en el campo "campo".

**Ejemplos:**
```
name:beach
description:pool
neighbourhood_cleansed:Hollywood
```

#### Búsqueda con Wildcards
```
campo:bea*
campo:*beach
campo:be?ch
```
- `*` = cualquier secuencia de caracteres
- `?` = un solo carácter
- `*beach` (comodín al inicio) requiere configuración especial

#### Búsqueda de Frases Exactas
```
campo:"exact phrase"
```
Busca la frase exacta entre comillas.

**Ejemplo:**
```
name:"Beach House"
description:"swimming pool"
```

#### Operadores Booleanos

**AND** (requiere ambos términos):
```
beach AND pool
name:beach AND description:pool
```

**OR** (cualquiera de los términos):
```
beach OR pool
name:beach OR name:ocean
```

**NOT** (excluye el término):
```
beach NOT pool
name:beach NOT description:pool
```

**Combinaciones:**
```
(beach OR ocean) AND pool
name:(beach OR ocean) AND amenities:wifi
```

#### Búsquedas Numéricas (Range Queries)

**Rangos:**
```
price:[100 TO 200]
price:[100 TO 200}
price:{100 TO 200]
review_scores_rating:[4.5 TO 5.0]
number_of_reviews:[10 TO *]
bedrooms:[2 TO 5]
```

- `[` `]` = inclusivo (incluye el valor)
- `{` `}` = exclusivo (excluye el valor)
- `*` = sin límite

**Ejemplos:**
```
price:[50 TO 150]          # Entre $50 y $150 (inclusivo)
price:[50 TO 150}          # $50 inclusivo, $150 exclusivo
price:[100 TO *]           # $100 o más
review_scores_rating:[4.0 TO 5.0]  # Rating entre 4.0 y 5.0
bedrooms:[2 TO *]          # 2 o más habitaciones
```

#### Búsquedas de Proximidad (Fuzzy)

**Fuzzy search** (búsqueda aproximada):
```
campo:beach~
campo:beach~2
```
- `~` = permite 1 error tipográfico
- `~2` = permite 2 errores

**Ejemplo:**
```
name:beah~  # Encontrará "beach" con un error tipográfico
```

#### Boost (Aumentar Relevancia)

```
beach^2 pool
name:beach^3 OR description:beach
```
- `^2` = dobla la relevancia
- `^3` = triplica la relevancia

---

## 🏠 Ejemplos Prácticos para Airbnb

### Esquema de Campos - Propiedades (`index_properties`)

| Campo | Tipo | Búsqueda | Ejemplo |
|-------|------|----------|---------|
| `id` | IntPoint | `id:2708` | `id:2708` |
| `name` | TextField | `name:beach` | `name:"Beach House"` |
| `description` | TextField (EnglishAnalyzer) | `description:pool` | `description:swimming` |
| `neighbourhood_cleansed` | StringField + FacetField | `neighbourhood_cleansed:Hollywood` | `neighbourhood_cleansed:Echo\ Park` ⭐ Categoría |
| `location` | LatLonPoint | Búsqueda geográfica | Ver más abajo |
| `property_type` | StringField + FacetField | `property_type:Entire\ home/apt` | `property_type:Private\ room` ⭐ Categoría |
| `amenity` | TextField (multivaluado) | `amenity:wifi` | `amenity:pool OR amenity:beach` |
| `amenities` | TextField | `amenities:wifi pool` | `amenities:"wifi pool"` |
| `price` | DoublePoint | `price:[100 TO 200]` | `price:[50 TO 150]` |
| `number_of_reviews` | IntPoint | `number_of_reviews:[10 TO *]` | `number_of_reviews:[5 TO 50]` |
| `review_scores_rating` | DoublePoint | `review_scores_rating:[4.5 TO 5.0]` | `review_scores_rating:[4.0 TO *]` |
| `bathrooms` | IntPoint | `bathrooms:[1 TO 2]` | `bathrooms:2` |
| `bedrooms` | IntPoint | `bedrooms:[2 TO 3]` | `bedrooms:[1 TO *]` |
| `host_id` | StringField | `host_id:3008` | `host_id:3008` |

### Esquema de Campos - Hosts (`index_hosts`)

| Campo | Tipo | Búsqueda | Ejemplo |
|-------|------|----------|---------|
| `host_id` | StringField | `host_id:3008` | `host_id:3008` |
| `host_name` | TextField | `host_name:John` | `host_name:"John Smith"` |
| `host_since` | LongPoint | `host_since:[2008-09-16 TO *]` | Ver más abajo |
| `host_location` | TextField (EnglishAnalyzer) | `host_location:"Los Angeles"` | `host_location:California` |
| `host_about` | TextField (EnglishAnalyzer) | `host_about:professional` | `host_about:writer` |
| `host_response_time` | StringField + FacetField | `host_response_time:within\ an\ hour` | `host_response_time:within\ a\ few\ hours` ⭐ Categoría |
| `host_is_superhost` | IntPoint (0/1) | `host_is_superhost:1` | `host_is_superhost:1` |

---

## 💡 Ejemplos de Búsquedas Específicas

### 1. Buscar Propiedades por Nombre
```
name:beach
name:"Beach House"
name:beach OR name:ocean
```

### 2. Buscar por Descripción
```
description:pool
description:"swimming pool"
description:(pool OR jacuzzi)
```

### 3. Buscar por Ubicación (Neighbourhood)
```
neighbourhood_cleansed:Hollywood
neighbourhood_cleansed:"Santa Monica"
neighbourhood_cleansed:(Hollywood OR Venice)
```

### 4. Buscar por Tipo de Propiedad
```
property_type:"Entire home/apt"
property_type:"Private room"
property_type:"Entire home/apt" OR property_type:"Entire rental unit"
```

### 5. Buscar por Amenidades
```
amenity:wifi
amenity:pool
amenity:wifi AND amenity:parking
amenity:(pool OR jacuzzi OR beach)
amenities:"wifi pool parking"
```

### 6. Buscar por Precio
```
price:[50 TO 100]
price:[100 TO *]
price:[* TO 150]
price:[80 TO 120]
```

### 7. Buscar por Rating
```
review_scores_rating:[4.5 TO 5.0]
review_scores_rating:[4.0 TO *]
review_scores_rating:[* TO 3.0]
```

### 8. Buscar por Número de Reviews
```
number_of_reviews:[10 TO *]
number_of_reviews:[20 TO 100]
number_of_reviews:[* TO 50]
```

### 9. Buscar por Número de Habitaciones
```
bedrooms:[2 TO 3]
bedrooms:[1 TO *]
bedrooms:2
```

### 10. Buscar por Baños
```
bathrooms:[1 TO 2]
bathrooms:2
bathrooms:[2 TO *]
```

### 11. Búsquedas Combinadas (Complejas)

**Propiedad con piscina en Hollywood, precio $100-200, rating 4.5+**
```
amenity:pool AND neighbourhood_cleansed:Hollywood AND price:[100 TO 200] AND review_scores_rating:[4.5 TO 5.0]
```

**Casa completa en Santa Monica o Venice, con wifi y parking**
```
property_type:"Entire home/apt" AND neighbourhood_cleansed:(Santa\ Monica OR Venice) AND amenity:wifi AND amenity:parking
```
*Nota: Espacios en nombres deben escaparse con `\`*

**Apartamento con 2+ habitaciones, 1+ baños, precio razonable**
```
bedrooms:[2 TO *] AND bathrooms:[1 TO *] AND price:[50 TO 150] AND review_scores_rating:[4.0 TO *]
```

**Propiedades con buena calificación y muchas reviews**
```
review_scores_rating:[4.5 TO 5.0] AND number_of_reviews:[20 TO *]
```

### 12. Buscar Hosts

**Superhosts**
```
host_is_superhost:1
```

**Hosts que responden rápido**
```
host_response_time:"within an hour"
```

**Hosts en Los Angeles**
```
host_location:"Los Angeles"
```

**Hosts profesionales**
```
host_about:professional OR host_about:consultant OR host_about:writer
```

### 13. Búsqueda por ID Específico

**Propiedad por ID**
```
id:2708
```

**Host por ID**
```
host_id:3008
```

### 14. Búsquedas con Boost (Relevancia)

**Priorizar nombre sobre descripción**
```
name:beach^3 OR description:beach
```

**Priorizar rating alto**
```
name:beach AND review_scores_rating:[4.5 TO 5.0]^2
```

---

## 🏷️ Búsquedas por Categorías (Facets)

### ¿Qué son las Categorías?

Las **categorías** (también llamadas **facets**) son campos especiales que permiten filtrar y agrupar resultados de búsqueda. En tu índice, tienes **3 campos categóricos** que usan `FacetField`:

1. **`neighbourhood_cleansed`** (en propiedades)
2. **`property_type`** (en propiedades)
3. **`host_response_time`** (en hosts)

Estos campos están indexados con `FacetField`, lo que permite:
- Filtrar resultados por categoría
- Ver conteos de documentos por categoría
- Navegar jerárquicamente por categorías en Luke

### Cómo Usar Categorías en Luke

#### Método 1: Panel de Facetas (si está disponible)

1. Abre Luke con tu índice
2. Ve a la pestaña **Search**
3. Busca el **panel de facetas** o **panel de categorías** (puede estar en un panel lateral o en una pestaña separada)
4. Selecciona valores de categoría para filtrar automáticamente los resultados

**Nota:** No todas las versiones de Luke muestran el panel de facetas automáticamente. Si no lo ves, usa el Método 2.

#### Método 2: Búsquedas Directas con Categorías

Puedes buscar directamente usando los campos categóricos en la query:

**Búsqueda por Neighbourhood:**
```lucene
neighbourhood_cleansed:Hollywood
neighbourhood_cleansed:Fairfax
neighbourhood_cleansed:Echo\ Park
neighbourhood_cleansed:Santa\ Monica
```

**Búsqueda por Property Type:**
```lucene
property_type:Entire\ home/apt
property_type:Private\ room
property_type:Entire\ rental\ unit
```

**Búsqueda por Host Response Time (en índice de hosts):**
```lucene
host_response_time:within\ an\ hour
host_response_time:within\ a\ few\ hours
host_response_time:N/A
```

### Ver Valores Únicos de Categorías

Para ver todos los valores únicos de una categoría y sus frecuencias:

1. Ve a la pestaña **Overview**
2. Busca el campo categórico (ej: `neighbourhood_cleansed`)
3. Haz clic en el campo
4. Verás:
   - Lista de todos los valores únicos
   - Frecuencia de cada valor (número de documentos)
   - Ordenados alfabéticamente

**Ejemplo de valores que verás:**
```
Hollywood: 1234 documentos
Fairfax: 319 documentos
Echo Park: 369 documentos
Santa Monica: 456 documentos
...
```

### Búsquedas Combinadas con Categorías

Puedes combinar búsquedas normales con filtros categóricos:

**Propiedades con wifi en Hollywood:**
```lucene
amenity:wifi AND neighbourhood_cleansed:Hollywood
```

**Propiedades tipo casa completa en Santa Monica:**
```lucene
property_type:Entire\ home/apt AND neighbourhood_cleansed:Santa\ Monica
```

**Propiedades con pool en Fairfax, 2+ habitaciones:**
```lucene
amenity:pool AND neighbourhood_cleansed:Fairfax AND bedrooms:[2 TO *]
```

**Propiedades tipo privada en Echo Park, con buena calificación:**
```lucene
property_type:Private\ room AND neighbourhood_cleansed:Echo\ Park AND review_scores_rating:[4.5 TO 5.0]
```

**Propiedades con wifi Y parking, en Hollywood, tipo casa completa:**
```lucene
amenity:wifi AND amenity:parking AND neighbourhood_cleansed:Hollywood AND property_type:Entire\ home/apt
```

### Búsquedas Múltiples Categorías (OR)

**Propiedades en Hollywood O Fairfax:**
```lucene
neighbourhood_cleansed:(Hollywood OR Fairfax)
```

**Propiedades tipo casa completa O apartamento:**
```lucene
property_type:(Entire\ home/apt OR Entire\ rental\ unit)
```

**Propiedades con wifi en Hollywood O Santa Monica:**
```lucene
amenity:wifi AND neighbourhood_cleansed:(Hollywood OR Santa\ Monica)
```

### Tips para Búsquedas por Categorías

1. **Case-sensitive:** Los campos categóricos requieren coincidencia exacta de mayúsculas/minúsculas
   - ✅ `neighbourhood_cleansed:Fairfax` (correcto)
   - ❌ `neighbourhood_cleansed:fairfax` (puede no funcionar)

2. **Escapa espacios:** Usa `\` para escapar espacios en valores
   - ✅ `neighbourhood_cleansed:Echo\ Park`
   - ✅ `property_type:Entire\ home/apt`

3. **No uses comillas:** Los campos categóricos no permiten búsquedas de frase con comillas
   - ❌ `neighbourhood_cleansed:"Echo Park"` (error)
   - ✅ `neighbourhood_cleansed:Echo\ Park` (correcto)

4. **Combina con otros campos:** Puedes combinar categorías con cualquier otro campo
   ```lucene
   amenity:wifi AND neighbourhood_cleansed:Hollywood AND price:[100 TO 200] AND bedrooms:[2 TO 3]
   ```

### Ejemplos Prácticos de Categorías

**Buscar todas las propiedades en Hollywood:**
```lucene
neighbourhood_cleansed:Hollywood
```

**Buscar propiedades tipo casa completa:**
```lucene
property_type:Entire\ home/apt
```

**Buscar propiedades en Fairfax con wifi:**
```lucene
neighbourhood_cleansed:Fairfax AND amenity:wifi
```

**Buscar propiedades tipo privada en Echo Park con buena calificación:**
```lucene
property_type:Private\ room AND neighbourhood_cleansed:Echo\ Park AND review_scores_rating:[4.5 TO *]
```

**Buscar propiedades con pool en Hollywood, precio razonable:**
```lucene
amenity:pool AND neighbourhood_cleansed:Hollywood AND price:[100 TO 200]
```

---

## 🔧 Operaciones Comunes

### 1. Verificar que el Índice Funcionó

1. Abre Luke con el índice
2. Ve a la pestaña **Overview**
3. Verifica:
   - **Número de documentos**: Debe coincidir con el número de propiedades/hosts
   - **Campos indexados**: Debe mostrar todos los campos esperados
   - **Última actualización**: Debe ser reciente

### 2. Inspeccionar un Documento Específico

1. Ve a la pestaña **Documents**
2. Usa el slider o escribe el número de documento
3. Verifica:
   - Campos almacenados (Stored fields)
   - Valores de cada campo
   - Términos indexados (Terms)

### 3. Probar una Búsqueda

1. Ve a la pestaña **Search**
2. Escribe tu query en el campo de búsqueda
3. Haz clic en "Execute Query"
4. Revisa:
   - Número de resultados
   - Score de relevancia
   - Documentos encontrados

### 4. Ver Cómo se Analizó un Campo

1. Ve a **Documents**
2. Selecciona un documento
3. Haz clic en un campo (ej: `description`)
4. Ve a la pestaña **Analysis** o **Terms**
5. Verifica:
   - Cómo se tokenizó el texto
   - Términos generados
   - Stop words eliminadas (si aplica)

### 5. Ver Todos los Valores de un Campo

1. Ve a **Overview**
2. Haz clic en el campo (ej: `neighbourhood_cleansed`)
3. Verás:
   - Todos los valores únicos
   - Frecuencia de cada valor
   - Número de documentos con cada valor

---

## 🐛 Troubleshooting

### Error: "LEADING_WILDCARD_NOT_ALLOWED"

**Problema:** Intentaste usar `*` al inicio de un término.

**Solución:**
- ❌ No uses: `*beach`, `*:*santa`
- ✅ Usa: `beach`, `santa`, `*beach*` (si está habilitado)

### Error: "no segments* file found"

**Problema:** Intentaste abrir `index_root/` en lugar del índice específico.

**Solución:**
- ❌ No uses: `index_root/`
- ✅ Usa: `index_root/index_properties/` o `index_root/index_hosts/`

### Error: "IndexNotFoundException"

**Problema:** El índice no existe o está corrupto.

**Solución:**
1. Verifica que el índice existe: `ls -la index_root/index_properties/`
2. Verifica que hay archivos `segments_*` en el directorio
3. Reindexa si es necesario: `java -cp ... AirbnbIndexador --input ... --index-root ...`

### Búsqueda no Encuentra Resultados

**Posibles causas:**
1. **Campo no existe**: Verifica el nombre del campo en **Overview**
2. **Campo no indexado**: Algunos campos solo están stored, no indexados
3. **Análisis diferente**: El campo puede usar un analyzer diferente
4. **Tipo de campo incorrecto**: 
   - `StringField` requiere coincidencia exacta
   - `TextField` permite búsqueda de texto
   - `IntPoint`/`DoublePoint` requiere rangos numéricos

**Solución:**
1. Verifica el esquema en **Overview**
2. Inspecciona un documento en **Documents** para ver los valores
3. Prueba búsquedas más simples primero

### Búsqueda Numérica no Funciona

**Problema:** Los campos numéricos (`IntPoint`, `DoublePoint`) requieren rangos.

**Solución:**
- ❌ No uses: `price:100` (no funciona)
- ✅ Usa: `price:[100 TO 100]` o `price:[100 TO *]`

### Búsqueda de Frase Exacta no Funciona

**Problema:** El campo usa `TextField` con analyzer que tokeniza.

**Solución:**
- Usa comillas: `"exact phrase"`
- O busca términos individuales: `exact AND phrase`

### Error: "field was indexed without position data; cannot run PhraseQuery"

**Problema:** Intentaste usar una búsqueda de frase (`"santa monica"`) en un campo indexado como `StringField` con `KeywordAnalyzer`. Estos campos no guardan información de posición (position data), que es necesaria para búsquedas de frase.

**Campos afectados:**
- `neighbourhood_cleansed` (StringField con KeywordAnalyzer)
- `property_type` (StringField con KeywordAnalyzer)
- `host_response_time` (StringField con KeywordAnalyzer)
- `host_id` (StringField)
- Cualquier campo `StringField` (no `TextField`)

**Solución:**

❌ **NO uses comillas** (busca frase exacta):
```
neighbourhood_cleansed:"Santa Monica"    # ❌ ERROR
property_type:"Entire home/apt"         # ❌ ERROR
```

✅ **Usa el valor exacto** (sin comillas):
```
neighbourhood_cleansed:Santa\ Monica     # ✅ Correcto (escapa espacios)
neighbourhood_cleansed:"Santa Monica"   # ✅ Funciona si es exacto
property_type:Entire\ home/apt          # ✅ Correcto (escapa espacios)
```

✅ **O usa wildcards** (si necesitas buscar parcialmente):
```
neighbourhood_cleansed:Santa*           # ✅ Busca "Santa" seguido de cualquier cosa
neighbourhood_cleansed:*Monica          # ✅ Busca cualquier cosa seguido de "Monica" (si está habilitado)
```

✅ **O busca términos separados** (si el campo fuera TextField):
```
neighbourhood_cleansed:Santa AND neighbourhood_cleansed:Monica  # ❌ No funciona para StringField
```

**Nota importante:**
- `StringField` con `KeywordAnalyzer` guarda el valor como una sola unidad (keyword)
- No se tokeniza, no hay posiciones
- Requiere coincidencia exacta del valor completo
- Usa `\` para escapar espacios en nombres: `Santa\ Monica`

**Ejemplos correctos:**
```
# Buscar por neighbourhood exacto
neighbourhood_cleansed:Hollywood
neighbourhood_cleansed:Santa\ Monica
neighbourhood_cleansed:"Los Angeles"    # Si el valor tiene comillas

# Buscar por tipo de propiedad exacto
property_type:Entire\ home/apt
property_type:Private\ room

# Buscar por host response time exacto
host_response_time:within\ an\ hour
host_response_time:"within a few hours"
```

---

## 📝 Cheat Sheet Rápido

### Búsquedas Básicas
```
texto                                    # Buscar en todos los campos
campo:valor                              # Buscar en campo específico
campo:"frase exacta"                     # Frase exacta
campo:valor*                              # Wildcard al final
campo:*valor                              # Wildcard al inicio (requiere config)
```

### Operadores Booleanos
```
término1 AND término2                    # Ambos deben aparecer
término1 OR término2                     # Cualquiera puede aparecer
término1 NOT término2                    # Excluye término2
(término1 OR término2) AND término3     # Agrupación
```

### Búsquedas Numéricas
```
campo:[100 TO 200]                       # Rango inclusivo
campo:[100 TO 200}                       # 100 inclusivo, 200 exclusivo
campo:[100 TO *]                         # 100 o más
campo:[* TO 200]                         # 200 o menos
```

### Boost y Relevancia
```
campo:valor^2                            # Dobla relevancia
campo:valor^3                            # Triplica relevancia
```

### Ejemplos Airbnb Rápidos
```
amenity:pool                             # Con piscina
price:[100 TO 200]                       # Precio entre $100-$200
review_scores_rating:[4.5 TO 5.0]        # Rating 4.5+
bedrooms:[2 TO 3]                        # 2-3 habitaciones
neighbourhood_cleansed:Hollywood         # En Hollywood (categoría)
property_type:Entire\ home/apt          # Casa completa (categoría)
host_is_superhost:1                      # Superhost
```

### Búsquedas por Categorías
```
neighbourhood_cleansed:Fairfax          # Por neighbourhood (categoría)
property_type:Private\ room             # Por tipo (categoría)
host_response_time:within\ an\ hour     # Por response time (categoría)
neighbourhood_cleansed:Hollywood AND property_type:Entire\ home/apt  # Combinar categorías
```

---

## 🎓 Tips Avanzados

### 1. Usar Default Field
- Deja el "Default field" vacío para buscar en todos los campos
- O especifica un campo por defecto (ej: `name`)

### 2. Ver Scores de Relevancia
- Los resultados se ordenan por score (relevancia)
- Score más alto = más relevante

### 3. Analizar Búsquedas Complejas
- Empieza simple y agrega complejidad gradualmente
- Prueba cada parte de la query por separado

### 4. Verificar Análisis
- Usa la pestaña **Analysis** para ver cómo se tokeniza el texto
- Esto te ayuda a entender por qué una búsqueda funciona o no

### 5. Exportar Resultados
- Luke permite exportar resultados de búsqueda
- Útil para análisis posterior

---

## 📚 Recursos Adicionales

- **Documentación Lucene Query Syntax**: https://lucene.apache.org/core/documentation.html
- **Luke en GitHub**: https://github.com/apache/lucene/tree/main/lucene/luke
- **Lucene Query Parser**: Ver ejemplos en la documentación oficial

---

## ✅ Checklist para Verificar tu Índice

- [ ] El índice se abre sin errores en Luke
- [ ] El número de documentos coincide con lo esperado
- [ ] Todos los campos están presentes en Overview
- [ ] Puedes ver documentos individuales en Documents
- [ ] Las búsquedas básicas funcionan
- [ ] Las búsquedas numéricas funcionan con rangos
- [ ] Las búsquedas booleanas funcionan correctamente
- [ ] Los campos stored muestran valores correctos

---

**¡Feliz búsqueda! 🚀**

