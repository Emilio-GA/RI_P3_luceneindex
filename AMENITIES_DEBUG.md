# 🔍 Debugging de Amenidades en AirbnbIndexador

## 📋 Cómo Funcionan las Amenidades

### Estructura de Indexación

El código indexa las amenidades de **dos formas**:

1. **Campo `amenity`** (TextField, multivaluado):
   - Cada amenidad individual se añade como un campo separado
   - Ejemplo: `"Extra pillows and blankets"` → campo `amenity` con valor `"Extra pillows and blankets"`
   - Usa `StandardAnalyzer` (tokeniza y hace lowercase)
   - **Búsqueda**: `amenity:wifi` o `amenity:pool`

2. **Campo `amenities`** (TextField):
   - Todas las amenidades concatenadas con espacios
   - Ejemplo: `"Extra pillows and blankets Frigidaire gas stove Free street parking ..."`
   - Usa `StandardAnalyzer` (tokeniza y hace lowercase)
   - **Búsqueda**: `amenities:wifi` o `amenities:pool`

3. **Campo `amenities_raw`** (StoredField):
   - Valor original del JSON array sin procesar
   - **NO se puede buscar** (solo stored, no indexado)
   - Útil para ver el valor original

### Formato de Datos de Entrada

Las amenidades vienen del CSV como un array JSON:
```json
["Extra pillows and blankets", "Frigidaire gas stove", "Free street parking", ...]
```

El parser extrae cada amenidad entre comillas dobles.

---

## 🔍 Cómo Verificar en Luke

### Paso 1: Verificar que el Campo Existe

1. Abre Luke con el índice de propiedades
2. Ve a la pestaña **Overview**
3. Busca los campos:
   - `amenity` (debería aparecer)
   - `amenities` (debería aparecer)
   - `amenities_raw` (debería aparecer)

### Paso 2: Inspeccionar un Documento

1. Ve a la pestaña **Documents**
2. Selecciona un documento (ej: documento 0)
3. Busca los campos de amenidades:
   - `amenity`: Deberías ver múltiples valores (multivaluado)
   - `amenities`: Deberías ver un texto largo con todas las amenidades
   - `amenities_raw`: Deberías ver el JSON original

**Ejemplo esperado:**
```
amenity: "Extra pillows and blankets"
amenity: "Frigidaire gas stove"
amenity: "Free street parking"
...
amenities: "Extra pillows and blankets Frigidaire gas stove Free street parking ..."
amenities_raw: ["Extra pillows and blankets", "Frigidaire gas stove", ...]
```

### Paso 3: Ver Términos Indexados

1. En **Documents**, selecciona un documento
2. Haz clic en el campo `amenity` o `amenities`
3. Ve a la pestaña **Terms** o **Analysis**
4. Verifica:
   - Cómo se tokenizó cada amenidad
   - Qué términos se generaron
   - Si hay stop words eliminadas

**Ejemplo:**
- Amenidad: `"Extra pillows and blankets"`
- Términos generados: `extra`, `pillows`, `blankets` (stop word "and" eliminada)

---

## 🐛 Problemas Comunes y Soluciones

### Problema 1: Búsqueda No Encuentra Resultados

**Síntoma:** `amenity:wifi` no encuentra nada

**Posibles causas:**

1. **El término está en mayúsculas/minúsculas diferentes**
   - El `StandardAnalyzer` convierte todo a lowercase
   - ✅ Usa: `amenity:wifi` (lowercase)
   - ❌ No uses: `amenity:Wifi` o `amenity:WIFI`

2. **El término tiene espacios o caracteres especiales**
   - Ejemplo: `"Free street parking"` → busca `free AND street AND parking`
   - ✅ Usa: `amenity:free` o `amenity:parking`
   - ✅ O usa: `amenities:free AND amenities:parking`

3. **Buscas en el campo equivocado**
   - Hay dos campos: `amenity` (singular, multivaluado) y `amenities` (plural, concatenado)
   - ✅ Prueba ambos: `amenity:wifi` y `amenities:wifi`

**Solución:**
```
# Buscar amenidad específica
amenity:wifi
amenity:pool
amenity:parking

# Buscar en el campo concatenado
amenities:wifi
amenities:pool

# Buscar múltiples términos
amenity:wifi AND amenity:parking
amenities:(wifi AND parking)
```

### Problema 2: Búsqueda Encuentra Resultados Incorrectos

**Síntoma:** `amenity:pool` encuentra propiedades que no tienen piscina

**Posibles causas:**

1. **El término aparece en otra amenidad**
   - Ejemplo: `"Pool view"` contiene "pool" pero no es una piscina
   - ✅ Usa búsqueda más específica: `amenity:"pool"` o `amenity:Private\ pool`

2. **El término está en el campo `amenities` concatenado**
   - Si buscas `amenities:pool`, puede encontrar "pool" en cualquier lugar del texto
   - ✅ Usa `amenity:pool` para buscar amenidades individuales

**Solución:**
```
# Búsqueda exacta de frase
amenity:"pool"
amenity:"Private pool"

# Búsqueda con contexto
amenity:pool AND amenity:private
```

### Problema 3: No Puedo Ver Valores de Amenidades

**Síntoma:** En Luke no veo los campos `amenity` o `amenities`

**Posibles causas:**

1. **El índice no se indexó correctamente**
   - Verifica que el proceso de indexación completó sin errores
   - Revisa los logs del indexador

2. **El documento no tiene amenidades**
   - Algunos documentos pueden no tener amenidades
   - Prueba con otro documento

**Solución:**
1. Reindexa el CSV: `java -cp ... AirbnbIndexador --input example_listings.csv --index-root ./index_root`
2. Verifica en Overview que los campos existen
3. Prueba con diferentes documentos

---

## ✅ Búsquedas Correctas de Amenidades

### Búsquedas Básicas

```lucene
# Buscar amenidad específica (singular - recomendado)
amenity:wifi
amenity:pool
amenity:parking

# Buscar en campo concatenado (plural)
amenities:wifi
amenities:pool

# Buscar con múltiples términos
amenity:wifi AND amenity:parking
amenity:(wifi AND parking)
amenities:(wifi AND parking)
```

### Búsquedas con Frases

```lucene
# Frase exacta (si la amenidad tiene múltiples palabras)
amenity:"Free street parking"
amenity:"Private pool"
amenity:"Shared gym in building"

# Búsqueda parcial (términos individuales)
amenity:free AND amenity:parking
amenity:private AND amenity:pool
```

### Búsquedas Avanzadas

```lucene
# Propiedades con wifi Y parking
amenity:wifi AND amenity:parking

# Propiedades con pool O jacuzzi
amenity:pool OR amenity:jacuzzi

# Propiedades con wifi pero SIN pool
amenity:wifi NOT amenity:pool

# Propiedades con múltiples amenidades
amenity:wifi AND amenity:parking AND amenity:pool
```

### Búsquedas con Wildcards

```lucene
# Buscar amenidades que empiezan con "free"
amenity:free*

# Buscar amenidades que contienen "pool"
amenity:*pool*

# Buscar amenidades que terminan con "parking"
amenity:*parking
```

---

## 🧪 Pruebas de Verificación

### Test 1: Verificar Indexación

1. Abre Luke
2. Ve a **Documents** → documento 0
3. Verifica que ves:
   - Múltiples campos `amenity` (multivaluado)
   - Un campo `amenities` (texto concatenado)
   - Un campo `amenities_raw` (JSON original)

### Test 2: Verificar Búsqueda Simple

Prueba estas búsquedas en **Search**:
```
amenity:wifi
amenity:pool
amenity:parking
```
Deberías obtener resultados.

### Test 3: Verificar Búsqueda Compleja

Prueba:
```
amenity:wifi AND amenity:parking
amenity:pool OR amenity:jacuzzi
```
Deberías obtener resultados que cumplan las condiciones.

### Test 4: Verificar Tokenización

1. Ve a **Documents** → documento 0
2. Haz clic en `amenity` o `amenities`
3. Ve a **Terms** o **Analysis**
4. Verifica que los términos están en lowercase
5. Verifica que stop words fueron eliminadas

---

## 📝 Ejemplos de Amenidades Reales

Basado en el CSV, estas son amenidades comunes:

```
Extra pillows and blankets
Frigidaire gas stove
Free street parking
Shared patio or balcony
Essentials
Outdoor furniture
Shared gym in building
Hot water kettle
Portable fans
Clothing storage: closet, wardrobe, and dresser
Paid dryer – In building
Carbon monoxide alarm
Central air conditioning
Elevator
Dishes and silverware
Smoke alarm
Bathtub
Long term stays allowed
Indoor fireplace: gas
```

**Búsquedas que deberían funcionar:**
```
amenity:wifi
amenity:pool
amenity:parking
amenity:fireplace
amenity:elevator
amenity:bathtub
amenity:air AND amenity:conditioning
amenity:carbon AND amenity:monoxide
```

---

## 🔧 Debugging en Luke

### Paso a Paso para Debuggear

1. **Verificar que el campo existe:**
   - Overview → Busca `amenity` y `amenities`

2. **Ver un documento:**
   - Documents → Selecciona documento 0
   - Busca campos `amenity`, `amenities`, `amenities_raw`

3. **Ver términos:**
   - Haz clic en `amenity` o `amenities`
   - Ve a Terms/Analysis
   - Verifica cómo se tokenizó

4. **Probar búsqueda:**
   - Search → Escribe `amenity:wifi`
   - Verifica resultados

5. **Si no funciona:**
   - Prueba con `amenities:wifi` (campo plural)
   - Prueba con términos más simples: `amenity:free`
   - Verifica que el término esté en lowercase

---

## 💡 Tips

1. **Usa `amenity` (singular)** para buscar amenidades individuales
2. **Usa `amenities` (plural)** para buscar en el texto concatenado
3. **Siempre lowercase** en búsquedas (StandardAnalyzer convierte todo)
4. **Usa comillas** para frases exactas: `amenity:"Free street parking"`
5. **Combina con AND/OR** para búsquedas complejas

---

## ❓ Preguntas Frecuentes

**Q: ¿Por qué hay dos campos (`amenity` y `amenities`)?**
A: `amenity` es multivaluado (cada amenidad individual), `amenities` es concatenado (todas juntas). Usa `amenity` para búsquedas específicas.

**Q: ¿Qué campo debo usar para buscar?**
A: Usa `amenity` (singular) para buscar amenidades individuales. Es más preciso.

**Q: ¿Por qué `amenity:WIFI` no funciona?**
A: El `StandardAnalyzer` convierte todo a lowercase. Usa `amenity:wifi`.

**Q: ¿Cómo busco amenidades con espacios?**
A: Usa comillas: `amenity:"Free street parking"` o busca términos individuales: `amenity:free AND amenity:parking`.

---

**¡Si sigues teniendo problemas, verifica los pasos de debugging arriba! 🔍**

