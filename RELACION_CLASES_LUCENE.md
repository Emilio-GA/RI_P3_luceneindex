# Relación entre las Clases Principales de Lucene para Búsquedas

Este documento explica la relación entre las clases más importantes de Apache Lucene utilizadas en el proceso de búsqueda: `IndexReader`, `IndexSearcher`, `QueryParser`, `Query`, `TopDocs` y `ScoreDoc`.

## Resumen Ejecutivo

El flujo de búsqueda en Lucene sigue esta secuencia:

```
IndexReader → IndexSearcher → QueryParser → Query → TopDocs → ScoreDoc → Document
```

Cada clase tiene un rol específico en el proceso de búsqueda y recuperación de información.

---

## 1. IndexReader - Acceso de Lectura al Índice

### Propósito
`IndexReader` es la clase que proporciona acceso de **solo lectura** al índice almacenado en disco.

### Características
- **Lee el índice** desde el sistema de archivos o memoria
- Proporciona acceso a los documentos indexados
- Es **thread-safe** y puede ser compartido por múltiples búsquedas simultáneas
- `DirectoryReader` es la implementación más común que abre un índice existente

### Ejemplo en el código

```java
IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
```

### Relación
- **Depende de**: `Directory` (índice en disco)
- **Usado por**: `IndexSearcher`
- **Propósito**: Abrir y leer el índice

---

## 2. IndexSearcher - Motor de Búsqueda

### Propósito
`IndexSearcher` es el motor que **ejecuta las búsquedas** sobre el índice.

### Características
- Se construye con un `IndexReader` (no puede existir sin él)
- Ejecuta búsquedas usando objetos `Query`
- Calcula los **scores de relevancia** usando una función de similitud (`Similarity`)
- Puede configurarse con diferentes algoritmos de similitud (BM25, TF-IDF, etc.)

### Ejemplo en el código

```java
IndexSearcher searcher = new IndexSearcher(reader);
searcher.setSimilarity(similarity);  // Configurar cómo se calcula la relevancia
```

### Relación
- **Depende de**: `IndexReader`
- **Usado por**: Tu código de aplicación
- **Propósito**: Ejecutar búsquedas y calcular relevancia

---

## 3. QueryParser - Conversión de Texto a Query

### Propósito
`QueryParser` convierte **texto plano del usuario** en un objeto `Query` estructurado que Lucene puede procesar.

### Características
- Parsea consultas en lenguaje natural o sintaxis de Lucene
- Usa un `Analyzer` para tokenizar y normalizar el texto de entrada
- Soporta sintaxis avanzada: `AND`, `OR`, `NOT`, comillas para frases, wildcards (`*`, `?`), etc.
- Puede configurarse con un campo por defecto para búsquedas

### Ejemplo en el código

```java
QueryParser parser = new QueryParser("description", analyzer);
Query query = parser.parse("apartment AND pool");
```

### Relación
- **Depende de**: `Analyzer` (para tokenización)
- **Usado por**: Tu código de aplicación
- **Produce**: Objetos `Query`
- **Propósito**: Convertir texto → Query estructurado

---

## 4. Query - Representación de la Consulta

### Propósito
`Query` es la **representación estructurada** de una consulta de búsqueda.

### Características
- Puede crearse programáticamente o mediante `QueryParser`
- Existen múltiples tipos de queries:
  - `TermQuery`: búsqueda de un término específico
  - `BooleanQuery`: combinación de queries con AND/OR/NOT
  - `PhraseQuery`: búsqueda de frases exactas
  - `RangeQuery`: búsquedas por rangos
  - `WildcardQuery`: búsquedas con comodines
- Es el objeto que `IndexSearcher` procesa

### Ejemplo en el código

```java
Query query;
try {
    query = parser.parse(line);  // QueryParser crea el Query
} catch (ParseException e) {
    // Manejo de errores
}
```

### Relación
- **Creado por**: `QueryParser` o código programático
- **Usado por**: `IndexSearcher.search()`
- **Propósito**: Representar la consulta de forma estructurada

---

## 5. TopDocs - Contenedor de Resultados

### Propósito
`TopDocs` es el **contenedor que almacena los resultados** de una búsqueda.

### Características
- Contiene los resultados ordenados por relevancia (score descendente)
- Incluye:
  - `totalHits`: número total de documentos que coinciden con la consulta
  - `scoreDocs`: array de `ScoreDoc` con los documentos encontrados
- El número de resultados puede limitarse (ej: top 100)

### Ejemplo en el código

```java
TopDocs hits = searcher.search(query, 100);  // Top 100 resultados
System.out.println("Docs encontrados: " + hits.totalHits.value());
```

### Relación
- **Creado por**: `IndexSearcher.search()`
- **Contiene**: Array de `ScoreDoc`
- **Propósito**: Almacenar resultados ordenados por relevancia

---

## 6. ScoreDoc - Documento con Score

### Propósito
`ScoreDoc` representa un **documento encontrado junto con su score de relevancia**.

### Características
- Cada `ScoreDoc` contiene:
  - `doc`: ID interno del documento en Lucene (no es el ID del documento original)
  - `score`: valor numérico de relevancia calculado por el algoritmo de similitud
- Los `ScoreDoc` están ordenados por score descendente
- Se usa el `doc` para recuperar el `Document` completo

### Ejemplo en el código

```java
for (ScoreDoc hit : hits.scoreDocs) {
    Document doc = searcher.storedFields().document(hit.doc);
    System.out.println("Score: " + hit.score);
    // ... mostrar información del documento
}
```

### Relación
- **Contenido en**: `TopDocs.scoreDocs` (array)
- **Usado para**: Recuperar el `Document` completo
- **Propósito**: Representar documento + relevancia

---

## Flujo Completo de Búsqueda

El proceso completo sigue esta secuencia:

```
1. IndexReader
   ↓ Abre el índice desde disco
   
2. IndexSearcher
   ↓ Se construye con IndexReader
   
3. QueryParser
   ↓ Parsea texto del usuario
   
4. Query
   ↓ Objeto de consulta estructurado
   
5. IndexSearcher.search(Query)
   ↓ Ejecuta la búsqueda
   
6. TopDocs
   ↓ Contiene resultados ordenados
   
7. ScoreDoc[]
   ↓ Array de documentos con scores
   
8. Document
   ↓ Se recupera usando searcher.doc(scoreDoc.doc)
```

---

## Ejemplo Completo del Código

Aquí está el flujo completo tal como aparece en `BusquedasLucene.java`:

```java
public void indexSearch(Analyzer analyzer, Similarity similarity) {
    // 1. Abrir el índice
    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
    
    // 2. Crear el buscador
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(similarity);
    
    // 3. Crear el parser de consultas
    QueryParser parser = new QueryParser("description", analyzer);
    
    while (true) {
        String line = in.readLine();  // Leer consulta del usuario
        
        // 4. Parsear la consulta
        Query query = parser.parse(line);
        
        // 5. Ejecutar búsqueda
        TopDocs hits = searcher.search(query, 100);
        
        // 6. Iterar sobre los resultados
        for (ScoreDoc hit : hits.scoreDocs) {
            // 7. Recuperar el documento completo
            Document doc = searcher.storedFields().document(hit.doc);
            
            // Mostrar información
            System.out.println("Score: " + hit.score);
            System.out.println("Nombre: " + doc.get("name"));
            // ...
        }
    }
    
    reader.close();
}
```

---

## Tabla de Relaciones

| Clase | Depende de | Usado por | Propósito |
|-------|------------|-----------|-----------|
| **IndexReader** | `Directory` (índice en disco) | `IndexSearcher` | Acceso de lectura al índice |
| **IndexSearcher** | `IndexReader` | Tu código | Ejecuta búsquedas y calcula relevancia |
| **QueryParser** | `Analyzer`, campo por defecto | Tu código | Convierte texto → Query |
| **Query** | - | `IndexSearcher` | Representa la consulta estructurada |
| **TopDocs** | - | Tu código | Contenedor de resultados ordenados |
| **ScoreDoc** | - | `TopDocs` | Documento + score de relevancia |

---

## Conceptos Clave

### ¿Por qué separar IndexReader e IndexSearcher?
- **IndexReader**: Abstrae el acceso al índice (puede ser desde disco, memoria, red, etc.)
- **IndexSearcher**: Encapsula la lógica de búsqueda y scoring (puede tener múltiples searchers sobre el mismo reader)

### ¿Por qué usar QueryParser?
- Permite que los usuarios escriban consultas en lenguaje natural o sintaxis de Lucene
- El `Analyzer` garantiza que el texto se procese igual que durante la indexación
- Facilita la creación de queries complejas sin programación

### ¿Qué es el score?
- El **score** es un valor numérico que indica qué tan relevante es un documento para la consulta
- Se calcula usando algoritmos como BM25 o TF-IDF
- Los documentos se ordenan por score descendente (más relevantes primero)

### ¿Qué es el doc ID?
- El `doc` en `ScoreDoc` es el **ID interno de Lucene**, no el ID del documento original
- Se usa para recuperar el `Document` completo con `searcher.doc(docId)`
- Si necesitas el ID original, debe estar almacenado como campo en el documento

---

## Referencias

- [Apache Lucene Documentation](https://lucene.apache.org/)
- Código fuente: `src/main/java/BusquedasLucene.java`
- Código fuente: `src/main/java/AirbnbIndexador.java`

