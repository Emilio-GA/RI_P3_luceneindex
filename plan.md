
#  Plan

Vamos a usarel conjunto de datos Los Angeles Airbnb Data (June 2025).
Nuestro objetivo será construir un programa que se pueda eje-
cutar en línea de comandos y que sea el encargado de generar/actualizar nuestro
índice.

## Campos y analizadores
El primer paso será configurar cada uno de los índices, identificando los cam-
pos y en su caso el analizador que se desee utilizar.


1. 
Vamos a indexar los siguientes campos que podemos separar en dos grandes grupos/tipos, propiedad y anfitrion.
Columnas de Propiedad:
 id, listing_url, name, description,
neighborhood_overview,
neighbourhood_cleansed, latitude,
longitude, property_type, bathrooms,
bathrooms_text , bedrooms,
amenities, price , number_of_reviews,
review_scores_rating

Columnas de Anfitrión:
 host_url, host_name, host_since,
host_location, host_about,
host_response_time, host_is_superhost,
host_neighbourhood


2.
Consideraremos tres tipos de información, no necesariamente
excluyente:
La suceptible de ser tratada como categorías, como el género
La numérica, como valoraciones, etc.
La eminentemente textual, que contiene descripción del atributo en lenguaje
natural


Consideramos tambien que Lucene permite hacer consultas por campos,
por lo que será necesario identificar los mismos. No todos los campos en un con-
junto de datos son importantes para las tareas de búsqueda y recuperación. A su
vez, un mismo campo podrá utilizarse para dos funciones distintas, por ejemplo
como texto sin tokenizar y texto tokenizado.

3.
Para procesar cada campo bien definimos esta tabla pero se aceptan sugerencias o alternativas logicas:
| Campo                 | Tipo Lucene   | StoredField | Analizador                     |
|------------------------|---------------|--------------|---------------------------------|
| id                    | IntPoint      | No           | KeywordAnalyzer                |
| name                  | TextField     | Sí           | StandardAnalyzer               |
| description            | TextField     | Sí           | EnglishAnalyzer                |
| neighbourhood_cleansed | StringField   | Sí           | KeywordAnalyzer                |
| latitude              | LatLonPoint   | Sí           | —                               |
| longitude             | LatLonPoint   | Sí           | —                               |
| property_type         | StringField   | Sí           | KeywordAnalyzer                |
| price                 | DoublePoint   | No           | —                               |
| number_of_reviews     | IntPoint      | No           | —                               |
| review_scores_rating  | DoublePoint   | No           | —                               |
| bathrooms             | IntPoint      | No           | —                               |
| bathrooms_text        | TextField     | Sí           | StandardAnalyzer o custom       |
| bedrooms              | IntPoint      | No           | —                               |
| host_id               | StringField   | No           | KeywordAnalyzer                |
| host_name             | TextField     | Sí           | StandardAnalyzer               |
| host_since            | fecha         | No           | ¿Existe DateAnalyzer?           |
| host_location         | —             | No           | EnglishAnalyzer                |
| host_about            | TextField     | Sí           | EnglishAnalyzer                |
| host_response_time    | StringField   | No           | KeywordAnalyzer                |
| host_is_superhost     | StringField   | No           | KeywordAnalyzer                |



## Información a almacenar
Como segundo paso, pasaremos al proceso de indexación propiamente dicho. Para ello, se deberá iden-
tificar la información que queremos almacenar como documento Lucene.


## Extracción de listings.csv
Extraer la información textual

## Procesamiento
Como ultimo paso, debemos procesarla como sea necesario para crear el documento Lucene con sus campos correspondientes y añadirlo al índice.


## Resultado
Se debe generar un ejecutable java (.jar) con todas las dependencias que reci-
birá los parámetros que permitan crear o bien crear un índice desde el principio
o añadir posibles nuevos capítulos a la colección. Para ello, un parámetro será el
tipo del fichero que contiene los datos así como el directorio donde se encuentran
estos. La corrección del índice creado la podemos ver si lo abrimos con Luke

# Uso de lucene
Para el estilo de codigo y funcionalida de Lucene es util ver los siguiente ejemplos

## Selección de analizador

```java
// Mapa de campo -> analizador
Map<String, Analyzer> analyzerPerField = new HashMap<>();
analyzerPerField.put("uncampo", new StandardAnalyzer());
analyzerPerField.put("otrocampo", new EnglishAnalyzer());

// Crear un PerFieldAnalyzerWrapper usando Whitespace como analizador por defecto
PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(
    new WhitespaceAnalyzer(), analyzerPerField
);
```

## Añadir campos

```java
Document doc = new Document();

doc.add(new StringField("isbn", "978-0071809252", Field.Store.YES));
// Por defecto TextField no se almacena; aquí lo almacenamos para inspección
doc.add(new TextField(
    "titulo",
    "Java: A Beginner’s Guide, Sixth Edition",
    Field.Store.YES
));
doc.add(new TextField(
    "contenido",
    "Fully updated for Java Platform, Standard Edition 8 (Java SE 8), Java..."
));

doc.add(new IntPoint("size", 148));
doc.add(new StoredField("size", 148));

doc.add(new SortedSetDocValuesField("format", new BytesRef("paperback")));
doc.add(new SortedSetDocValuesField("format", new BytesRef("kindle")));

Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse("2015-01-01T00:00:00Z");
doc.add(new LongPoint("Date", date.getTime()));
```

## Campo nuevo personalizado

```java
FieldType authorType = new FieldType();
authorType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
// authorType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
authorType.setStored(true);
authorType.setOmitNorms(true);
authorType.setTokenized(false);
authorType.setStoreTermVectors(true);

doc.add(new Field("author", "Arnaud Cogoluegnes", authorType));
doc.add(new Field("author", "Thierry Templier", authorType));
doc.add(new Field("author", "Gary Gregory", authorType));
```


