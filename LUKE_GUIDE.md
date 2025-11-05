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

**⚠️ Limitación de Luke:**
- ❌ Los campos `DoublePoint` (`price`, `review_scores_rating`) **NO funcionan en Luke Search**
- ✅ Los campos `IntPoint` (`number_of_reviews`, `bedrooms`, `bathrooms`) **SÍ funcionan en Luke Search**
- ✅ Todas las queries funcionan **perfectamente en código Java**

**Ejemplos:**
```
# Estos SÍ funcionan en Luke:
number_of_reviews:[10 TO *]     # ✅ Funciona
bedrooms:[2 TO *]               # ✅ Funciona
bathrooms:[1 TO 2]              # ✅ Funciona

# Estos NO funcionan en Luke (usa código Java):
price:[50 TO 150]               # ❌ ERROR en Luke
price:[50 TO 150}               # ❌ ERROR en Luke
price:[100 TO *]                # ❌ ERROR en Luke
review_scores_rating:[4.0 TO 5.0]  # ❌ ERROR en Luke
```

**Nota:** Los ejemplos de `price` y `review_scores_rating` funcionan correctamente en código Java, pero no en la interfaz de Luke. Ver sección [Troubleshooting](#troubleshooting) para más detalles.

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
| `neighbourhood_cleansed` | StringField | `neighbourhood_cleansed:Hollywood` | `neighbourhood_cleansed:"Los Angeles"` |
| `location` | LatLonPoint | Búsqueda geográfica | Ver más abajo |
| `property_type` | StringField | `property_type:"Entire home/apt"` | `property_type:"Private room"` |
| `amenity` | TextField (multivaluado) | `amenity:wifi` | `amenity:pool OR amenity:beach` |
| `amenities` | TextField | `amenities:wifi pool` | `amenities:"wifi pool"` |
| `price` | DoublePoint | `price:[100 TO 200]` ❌ | `price:[50 TO 150]` ❌ |
| `number_of_reviews` | IntPoint | `number_of_reviews:[10 TO *]` ✅ | `number_of_reviews:[5 TO 50]` ✅ |
| `review_scores_rating` | DoublePoint | `review_scores_rating:[4.5 TO 5.0]` ❌ | `review_scores_rating:[4.0 TO *]` ❌ |
| `bathrooms` | IntPoint | `bathrooms:[1 TO 2]` ✅ | `bathrooms:2` ✅ |
| `bedrooms` | IntPoint | `bedrooms:[2 TO 3]` ✅ | `bedrooms:[1 TO *]` ✅ |
| `host_id` | StringField | `host_id:3008` | `host_id:3008` |

**⚠️ Nota:** Los campos `DoublePoint` (`price`, `review_scores_rating`) **NO funcionan en Luke Search** debido a una limitación de la interfaz. Funcionan perfectamente en código Java. Ver sección [Troubleshooting](#troubleshooting) para más detalles.

### Esquema de Campos - Hosts (`index_hosts`)

| Campo | Tipo | Búsqueda | Ejemplo |
|-------|------|----------|---------|
| `host_id` | StringField | `host_id:3008` | `host_id:3008` |
| `host_name` | TextField | `host_name:John` | `host_name:"John Smith"` |
| `host_since` | LongPoint | `host_since:[2008-09-16 TO *]` | Ver más abajo |
| `host_location` | TextField (EnglishAnalyzer) | `host_location:"Los Angeles"` | `host_location:California` |
| `host_about` | TextField (EnglishAnalyzer) | `host_about:professional` | `host_about:writer` |
| `host_response_time` | StringField | `host_response_time:"within an hour"` | `host_response_time:"within a few hours"` |
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

**⚠️ Importante:** `neighbourhood_cleansed` es un `StringField` normalizado a lowercase. **NO uses comillas** (produce error).

```
neighbourhood_cleansed:hollywood
neighbourhood_cleansed:santa\ monica
neighbourhood_cleansed:(hollywood OR venice)
```

**Nota:** Si hay espacios, puedes escapar con `\`. Los valores están en lowercase.

### 4. Buscar por Tipo de Propiedad

**⚠️ Importante:** `property_type` es un `StringField` normalizado a lowercase. **NO uses comillas** (produce error).

**⚠️ Problema con "/":** El carácter "/" en "entire home/apt" es interpretado como parte de una query de rango. Necesitas escaparlo.

**Solución 1: Escapar el espacio y el slash:**
```
property_type:entire\ home\/apt
property_type:private\ room
```

**Solución 2: Verificar el valor exacto en Luke:**
1. Ve a **Overview** en Luke
2. Haz clic en el campo `property_type`
3. Haz clic en "Show top terms" para ver los valores exactos indexados
4. Usa el valor exacto que veas (ya escapado)

**Solución 3: Usar wildcards (si necesitas buscar parcialmente):**
```
property_type:entire*
property_type:*home*
```

**Ejemplos completos:**
```
property_type:entire\ home\/apt
property_type:private\ room
property_type:entire\ home\/apt OR property_type:entire\ rental\ unit
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

**⚠️ Limitación de Luke:** Los campos `DoublePoint` como `price` **NO funcionan en Luke Search**. Usa código Java para buscar por precio.

```
# Estos NO funcionan en Luke (usa código Java):
price:[50 TO 100]      # ❌ ERROR
price:[100 TO *]       # ❌ ERROR
price:[* TO 150]       # ❌ ERROR
price:[80 TO 120]      # ❌ ERROR
```

**Alternativa:** Usa la pestaña **Documents** para explorar documentos individuales y ver sus valores de `price`.

### 7. Buscar por Rating

**⚠️ Limitación de Luke:** Los campos `DoublePoint` como `review_scores_rating` **NO funcionan en Luke Search**. Usa código Java para buscar por rating.

```
# Estos NO funcionan en Luke (usa código Java):
review_scores_rating:[4.5 TO 5.0]  # ❌ ERROR
review_scores_rating:[4.0 TO *]     # ❌ ERROR
review_scores_rating:[* TO 3.0]    # ❌ ERROR
```

**Alternativa:** Usa la pestaña **Documents** para explorar documentos individuales y ver sus valores de `review_scores_rating`.

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

⚠️ **Nota:** Esta query incluye campos `DoublePoint` que no funcionan en Luke Search. Usa código Java.

```
# En Luke (sin price y rating):
amenity:pool AND neighbourhood_cleansed:Hollywood

# En código Java (funciona completo):
amenity:pool AND neighbourhood_cleansed:Hollywood AND price:[100 TO 200] AND review_scores_rating:[4.5 TO 5.0]
```

**Casa completa en Santa Monica o Venice, con wifi y parking**

**⚠️ Importante:** `property_type` y `neighbourhood_cleansed` son `StringField` normalizados a lowercase. **NO uses comillas**. Necesitas escapar espacios y el carácter "/".

```
property_type:entire\ home\/apt AND neighbourhood_cleansed:(santa\ monica OR venice) AND amenity:wifi AND amenity:parking
```

*Nota: Espacios y "/" deben escaparse con `\`. Los valores están en lowercase.*

**Apartamento con 2+ habitaciones, 1+ baños, precio razonable**

⚠️ **Nota:** Esta query incluye campos `DoublePoint` que no funcionan en Luke Search. Usa código Java.

```
# En Luke (sin price y rating):
bedrooms:[2 TO *] AND bathrooms:[1 TO *]

# En código Java (funciona completo):
bedrooms:[2 TO *] AND bathrooms:[1 TO *] AND price:[50 TO 150] AND review_scores_rating:[4.0 TO *]
```

**Propiedades con buena calificación y muchas reviews**

⚠️ **Nota:** Esta query incluye `review_scores_rating` que no funciona en Luke Search. Usa código Java.

```
# En Luke (solo number_of_reviews):
number_of_reviews:[20 TO *]

# En código Java (funciona completo):
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

### Campos con 0 Términos en Luke

**Problema:** Algunos campos muestran "Term count: 0" en la pestaña **Overview** de Luke.

**Explicación:** Esto es **NORMAL** y **ESPERADO**. No significa que el campo esté vacío o mal indexado.

**Campos que muestran 0 términos (y por qué):**

#### 1. Campos Point (IntPoint, DoublePoint, LatLonPoint)
Estos campos **NO generan términos** en el índice invertido tradicional. Se indexan usando estructuras de datos especiales (BKD trees) para búsquedas eficientes de rangos y geográficas.

**Campos afectados:**
- `id` (IntPoint) - 0 términos ✅ **NORMAL**
- `price` (DoublePoint) - 0 términos ✅ **NORMAL**
- `review_scores_rating` (DoublePoint) - 0 términos ✅ **NORMAL**
- `number_of_reviews` (IntPoint) - 0 términos ✅ **NORMAL**
- `bathrooms` (IntPoint) - 0 términos ✅ **NORMAL**
- `bedrooms` (IntPoint) - 0 términos ✅ **NORMAL**
- `location` (LatLonPoint) - 0 términos ✅ **NORMAL**

**Cómo verificar que funcionan:**
1. Ve a la pestaña **Documents** en Luke
2. Selecciona un documento
3. Verifica que los campos stored muestran los valores correctos
4. Usa búsquedas por rango en código Java (funcionan perfectamente)

#### 2. Campos StoredField solamente (no indexados)
Estos campos solo almacenan valores, pero **NO se indexan** como términos.

**Campos afectados:**
- `latitude` (StoredField) - 0 términos ✅ **NORMAL**
- `longitude` (StoredField) - 0 términos ✅ **NORMAL**
- `property_type_original` (StoredField) - 0 términos ✅ **NORMAL**
- `neighbourhood_cleansed_original` (StoredField) - 0 términos ✅ **NORMAL**
- `host_response_time_original` (StoredField) - 0 términos ✅ **NORMAL**

**Cómo verificar que funcionan:**
1. Ve a la pestaña **Documents** en Luke
2. Selecciona un documento
3. Verifica que los campos stored muestran los valores correctos

#### 3. Campos que SÍ tienen términos
Estos campos generan términos en el índice invertido y aparecen con conteos > 0:

- `host_id` (StringField) - ✅ Tiene términos
- `description` (TextField) - ✅ Tiene términos
- `name` (TextField) - ✅ Tiene términos
- `amenity` (TextField multivaluado) - ✅ Tiene términos
- `neighbourhood_cleansed` (StringField) - ✅ Tiene términos
- `property_type` (StringField) - ✅ Tiene términos

**Resumen:**
- **0 términos** en campos Point o StoredField = ✅ **NORMAL, esperado**
- **0 términos** en campos TextField/StringField = ⚠️ **Puede ser un problema** (verificar que los datos existen)

**Nota importante:** Los campos Point (IntPoint, DoublePoint, LatLonPoint) están **correctamente indexados** aunque Luke muestre 0 términos. Las búsquedas por rango funcionan perfectamente en código Java.

#### Verificar que `location` está indexado (aunque muestre 0 términos)

El campo `location` (LatLonPoint) **siempre muestra 0 términos** en Luke, pero esto es normal. Para verificar que está correctamente indexado:

1. **Ve a la pestaña Documents**
2. **Selecciona varios documentos** (usa el slider o escribe números)
3. **Verifica que los campos stored `latitude` y `longitude` tienen valores**:
   - Si un documento tiene `latitude: 34.09625` y `longitude: -118.34605`, entonces `location` está indexado correctamente
   - Si `latitude` y `longitude` están vacíos, entonces el documento no tiene coordenadas geográficas en el CSV

**Nota:** Si `location` está vacío en muchos documentos, puede ser porque:
- El CSV no tiene valores de `latitude`/`longitude` para esos documentos
- El parsing del CSV está fallando (por ejemplo, si las comas dentro de comillas no se manejan correctamente)
- Los valores no son numéricos válidos y `parseDouble()` devuelve `null`

**Solución:** Verifica que el CSV tiene valores válidos en las columnas `latitude` y `longitude`, y que el parser CSV está manejando correctamente las comas dentro de campos con comillas.

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

✅ **Usa el valor exacto** (sin comillas, escapando espacios y caracteres especiales):
```
neighbourhood_cleansed:santa\ monica     # ✅ Correcto (escapa espacios, lowercase)
property_type:entire\ home\/apt         # ✅ Correcto (escapa espacio Y "/", lowercase)
property_type:private\ room              # ✅ Correcto (escapa espacios)
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

# Buscar por tipo de propiedad exacto (lowercase, escapando espacio Y "/")
property_type:entire\ home\/apt
property_type:private\ room

# Buscar por host response time exacto
host_response_time:within\ an\ hour
host_response_time:"within a few hours"
```

### Error: "Field price is indexed with 8 bytes per dimension, but IntComparator expected 4"
### Error: "field was indexed with bytesPerDim=8 but this query has bytesPerDim=4"

**Problema:** Intentaste usar un campo `DoublePoint` (como `price` o `review_scores_rating`) en la pestaña **Search** de Luke, ya sea para búsqueda con rangos o para ordenamiento. Luke está intentando usar un formato de 4 bytes (IntPoint) en lugar de 8 bytes (DoublePoint) para procesar estos campos.

**Mensajes de error que puedes ver:**
- `Field price is indexed with 8 bytes per dimension, but IntComparator expected 4`
- `field="review_scores_rating" was indexed with bytesPerDim=8 but this query has bytesPerDim=4`

Ambos errores indican el mismo problema: Luke está intentando usar `IntPoint` (4 bytes) en lugar de `DoublePoint` (8 bytes).

**Causa:** Esta es una limitación conocida de la interfaz de Luke. Cuando usas un campo `DoublePoint` en una búsqueda (incluso en range queries como `price:[1 TO 100]` o `review_scores_rating:[3 TO 4]`) o intentas ordenar por él, Luke detecta incorrectamente el tipo de campo y crea un query con el tipo numérico equivocado.

**Campos afectados:**
- `price` (DoublePoint - 8 bytes) ❌ **NO funciona en Luke Search**
- `review_scores_rating` (DoublePoint - 8 bytes) ❌ **NO funciona en Luke Search**
- Cualquier campo indexado como `DoublePoint` (no `IntPoint`)

**⚠️ Limitación importante:**
❌ **NO puedes usar campos `DoublePoint` en la pestaña Search de Luke:**
- ❌ No uses range queries: `price:[1 TO 100]` → **ERROR**
- ❌ No uses sorting por estos campos → **ERROR**
- ❌ Incluso combinaciones fallan: `price:[100 TO 200] AND name:beach` → **ERROR**

✅ **Campos que SÍ funcionan en Luke Search:**
- `number_of_reviews` (IntPoint) ✅ Funciona
- `bedrooms` (IntPoint) ✅ Funciona
- `bathrooms` (IntPoint) ✅ Funciona
- Todos los campos `IntPoint` ✅ Funcionan correctamente

**Solución y alternativas:**

1. **Usa la pestaña Documents para explorar:**
   - Navega por documentos individuales
   - Verifica que los valores de `price` y `review_scores_rating` están correctamente indexados
   - Revisa los valores stored para confirmar que los datos están bien

2. **Escribe código Java para buscar:**
   - Las búsquedas con `DoublePoint` funcionan **perfectamente** en código Java
   - Puedes usar `DoublePoint.newRangeQuery()` o queries con rangos
   - Puedes ordenar usando `DoubleComparator` correctamente
   - Este es un problema **solo de la interfaz de Luke**, no de Lucene

3. **Busca por otros campos que sí funcionan:**
   ```
   # Estos funcionan en Luke:
   name:beach
   description:pool
   neighbourhood_cleansed:Hollywood
   number_of_reviews:[10 TO *]
   bedrooms:[2 TO 3]
   bathrooms:[1 TO 2]
   
   # Estos NO funcionan en Luke (usa código Java):
   price:[100 TO 200]              # ❌ ERROR
   review_scores_rating:[4.5 TO 5.0]  # ❌ ERROR
   ```

**Nota importante:**
- El índice está **correctamente configurado** - `price` está indexado como `DoublePoint` (8 bytes)
- Este es un **problema de la interfaz de Luke**, no de tu código de indexación
- **Lucene funciona perfectamente** - puedes usar estos campos normalmente en tu aplicación Java
- Si necesitas buscar por `price` o `review_scores_rating`, escribe código Java usando las APIs de Lucene

**Ejemplo de búsqueda que funciona en Luke:**
```
# Buscar por otros campos (funciona)
name:beach AND neighbourhood_cleansed:Hollywood
number_of_reviews:[10 TO *] AND bedrooms:[2 TO 3]
amenity:pool AND bathrooms:[1 TO 2]
```

**Ejemplo de código Java para buscar por price (funciona correctamente):**
```java
// Esto funciona perfectamente en código Java
Query priceQuery = DoublePoint.newRangeQuery("price", 100.0, 200.0);
Query nameQuery = new TermQuery(new Term("name", "beach"));
BooleanQuery combined = new BooleanQuery.Builder()
    .add(priceQuery, BooleanClause.Occur.MUST)
    .add(nameQuery, BooleanClause.Occur.MUST)
    .build();
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
# IntPoint (funciona en Luke):
campo:[100 TO 200]                       # Rango inclusivo
campo:[100 TO 200}                       # 100 inclusivo, 200 exclusivo
campo:[100 TO *]                         # 100 o más
campo:[* TO 200]                         # 200 o menos

# DoublePoint (NO funciona en Luke, usa código Java):
price:[100 TO 200]                       # ❌ ERROR en Luke
review_scores_rating:[4.5 TO 5.0]        # ❌ ERROR en Luke
```

### Boost y Relevancia
```
campo:valor^2                            # Dobla relevancia
campo:valor^3                            # Triplica relevancia
```

### Ejemplos Airbnb Rápidos
```
# Estos funcionan en Luke:
amenity:pool                             # Con piscina
bedrooms:[2 TO 3]                        # 2-3 habitaciones
neighbourhood_cleansed:hollywood         # En Hollywood (lowercase, sin comillas)
property_type:entire home/apt            # Casa completa (lowercase, sin comillas)
host_is_superhost:1                      # Superhost

# Estos NO funcionan en Luke (usa código Java):
price:[100 TO 200]                       # ❌ ERROR en Luke
review_scores_rating:[4.5 TO 5.0]        # ❌ ERROR en Luke
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

