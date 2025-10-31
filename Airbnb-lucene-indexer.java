/*
README - Airbnb Lucene Indexer

Descripción
-----------
Aplicación Java que crea/actualiza índices Lucene para los datos de AirBnB.
Genera un JAR ejecutable "fat jar" con todas las dependencias (usando Maven + Shade)
Permite 2 tipos de documento/índice: "property" y "host".

Características principales
--------------------------
- Crea o actualiza un índice (CREATE o APPEND/UPDATE) sin reindexar todo.
- Cuando se añaden documentos nuevos con el mismo id se actualizan (updateDocument).
- Soporta entrada en formato CSV (archivo con cabecera). Se puede extender a JSON.
- PerField analyzers según tu especificación (KeywordAnalyzer, StandardAnalyzer, EnglishAnalyzer).
- Almacena (StoredField) solo aquellos campos necesarios para mostrar.
- Los campos numéricos se indexan con Point (IntPoint, DoublePoint, LongPoint) y, si se necesitan recuperar, también con StoredField.

Uso
---
Construcción (Maven):
  mvn package
Esto genera target/airbnb-indexer-1.0.jar (fat jar) con todas las dependencias.

Ejecución:
  java -jar target/airbnb-indexer-1.0.jar --action create|update --type property|host --input /ruta/al/fichero.csv --indexDir /ruta/al/dirIndice

Ejemplos:
  Crear índice de propiedades desde properties.csv:
    java -jar target/airbnb-indexer-1.0.jar --action create --type property --input data/properties.csv --indexDir indexes/properties

  Añadir/actualizar documentos al índice de anfitriones:
    java -jar target/airbnb-indexer-1.0.jar --action update --type host --input data/hosts.csv --indexDir indexes/hosts

Notas de diseño
---------------
- El campo identificador se almacena como StringField "id_str" (Field.Store.YES) para poder recuperar y usar updateDocument.
- Se añade el mismo valor como IntPoint `id_int` si se necesita búsquedas numéricas por id.
- Los campos lat/long se indexan con LatLonPoint y se almacenan en StoredField latitude/longitude para mostrar.
- Para fechas (host_since) se usa LongPoint con epoch millis y StoredField para recuperación.
- Los documentos con campos vacíos quedan con esos campos vacíos; durante ordenación o ranking puedes tratar los nulls para enviarlos al final.
*/

package com.example.airbnb;

import com.opencsv.CSVReader;
import org.apache.commons.cli.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.LatLonPoint;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Indexer para datos de Airbnb - Adaptado para el formato específico del CSV
 */
public class Indexer {

    private static final SimpleDateFormat DATE_PARSER = new SimpleDateFormat("yyyy-MM-dd");

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addRequiredOption(null, "action", true, "create | update");
        options.addRequiredOption(null, "type", true, "property | host");
        options.addRequiredOption(null, "input", true, "CSV input file path");
        options.addRequiredOption(null, "indexDir", true, "Directory for Lucene index");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        String action = cmd.getOptionValue("action");
        String type = cmd.getOptionValue("type");
        String input = cmd.getOptionValue("input");
        String indexDir = cmd.getOptionValue("indexDir");

        boolean create = "create".equalsIgnoreCase(action);

        try (FSDirectory dir = FSDirectory.open(Paths.get(indexDir))) {
            IndexWriterConfig.OpenMode mode = create ? IndexWriterConfig.OpenMode.CREATE : IndexWriterConfig.OpenMode.CREATE_OR_APPEND;

            // Configuración de analizadores por campo
            Analyzer defaultAnalyzer = new StandardAnalyzer();
            Map<String, Analyzer> perField = new HashMap<>();
            perField.put("id_str", new KeywordAnalyzer());
            perField.put("neighbourhood_cleansed", new KeywordAnalyzer());
            perField.put("property_type", new KeywordAnalyzer());
            perField.put("host_response_time", new KeywordAnalyzer());
            perField.put("host_is_superhost", new KeywordAnalyzer());
            perField.put("description", new EnglishAnalyzer());
            perField.put("host_about", new EnglishAnalyzer());
            perField.put("neighborhood_overview", new EnglishAnalyzer());

            PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(defaultAnalyzer, perField);
            IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
            cfg.setOpenMode(mode);

            try (IndexWriter writer = new IndexWriter(dir, cfg)) {
                if ("property".equalsIgnoreCase(type)) {
                    indexProperties(writer, input);
                } else if ("host".equalsIgnoreCase(type)) {
                    indexHosts(writer, input);
                } else {
                    System.err.println("Unknown type: " + type);
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Indexa propiedades desde el CSV con el formato específico
     */
    private static void indexProperties(IndexWriter writer, String csvPath) throws IOException {
        try (CSVReader r = new CSVReader(new FileReader(csvPath))) {
            String[] header = r.readNext();
            if (header == null) throw new IOException("CSV vacío: " + csvPath);
            Map<String, Integer> idx = headerMap(header);

            String[] line;
            int count = 0;
            while ((line = r.readNext()) != null) {
                Document doc = new Document();

                // ID
                String id = safeGet(line, idx.get("id"));
                if (id == null || id.isEmpty()) {
                    System.err.println("Documento sin id en linea " + (count+2) + " - se ignora");
                    continue;
                }
                doc.add(new StringField("id_str", id, Field.Store.YES));
                try {
                    int idInt = Integer.parseInt(id);
                    doc.add(new IntPoint("id_int", idInt));
                    doc.add(new StoredField("id_int", idInt));
                } catch (NumberFormatException e) {
                    // omitimos id_int si no es entero
                }

                // Campos básicos
                String name = safeGet(line, idx.get("name"));
                if (name != null) doc.add(new TextField("name", name, Field.Store.YES));

                String desc = safeGet(line, idx.get("description"));
                if (desc != null) doc.add(new TextField("description", desc, Field.Store.YES));

                String neighborhoodOverview = safeGet(line, idx.get("neighborhood_overview"));
                if (neighborhoodOverview != null) doc.add(new TextField("neighborhood_overview", neighborhoodOverview, Field.Store.YES));

                // Ubicación
                String neigh = safeGet(line, idx.get("neighbourhood_cleansed"));
                if (neigh != null) doc.add(new StringField("neighbourhood_cleansed", neigh, Field.Store.YES));

                String latS = safeGet(line, idx.get("latitude"));
                String lonS = safeGet(line, idx.get("longitude"));
                if (latS != null && lonS != null && !latS.isEmpty() && !lonS.isEmpty()) {
                    try {
                        double lat = Double.parseDouble(latS);
                        double lon = Double.parseDouble(lonS);
                        doc.add(new LatLonPoint("location", lat, lon));
                        doc.add(new StoredField("latitude", lat));
                        doc.add(new StoredField("longitude", lon));
                    } catch (NumberFormatException e) {
                        // ignora coordenadas inválidas
                    }
                }

                // Tipo de propiedad
                String propType = safeGet(line, idx.get("property_type"));
                if (propType != null) doc.add(new StringField("property_type", propType, Field.Store.YES));

                String roomType = safeGet(line, idx.get("room_type"));
                if (roomType != null) doc.add(new StringField("room_type", roomType, Field.Store.YES));

                // Precio (limpiar formato $)
                String priceS = safeGet(line, idx.get("price"));
                if (priceS != null && !priceS.isEmpty()) {
                    try {
                        // Remover $ y comas, luego convertir a double
                        String cleanPrice = priceS.replace("$", "").replace(",", "");
                        double price = Double.parseDouble(cleanPrice);
                        doc.add(new DoublePoint("price", price));
                        doc.add(new StoredField("price", price));
                    } catch (NumberFormatException e) { 
                        System.err.println("Precio inválido: " + priceS);
                    }
                }

                // Capacidad y habitaciones
                String accommodatesS = safeGet(line, idx.get("accommodates"));
                if (accommodatesS != null && !accommodatesS.isEmpty()) {
                    try {
                        int accommodates = Integer.parseInt(accommodatesS);
                        doc.add(new IntPoint("accommodates", accommodates));
                        doc.add(new StoredField("accommodates", accommodates));
                    } catch (NumberFormatException e) { }
                }

                String bathroomsS = safeGet(line, idx.get("bathrooms"));
                if (bathroomsS != null && !bathroomsS.isEmpty()) {
                    try {
                        double bathrooms = Double.parseDouble(bathroomsS);
                        doc.add(new DoublePoint("bathrooms", bathrooms));
                        doc.add(new StoredField("bathrooms", bathrooms));
                    } catch (NumberFormatException e) { }
                }

                String bathroomsText = safeGet(line, idx.get("bathrooms_text"));
                if (bathroomsText != null) doc.add(new TextField("bathrooms_text", bathroomsText, Field.Store.YES));

                String bedroomsS = safeGet(line, idx.get("bedrooms"));
                if (bedroomsS != null && !bedroomsS.isEmpty()) {
                    try {
                        double bedrooms = Double.parseDouble(bedroomsS);
                        doc.add(new DoublePoint("bedrooms", bedrooms));
                        doc.add(new StoredField("bedrooms", bedrooms));
                    } catch (NumberFormatException e) { }
                }

                String bedsS = safeGet(line, idx.get("beds"));
                if (bedsS != null && !bedsS.isEmpty()) {
                    try {
                        double beds = Double.parseDouble(bedsS);
                        doc.add(new DoublePoint("beds", beds));
                        doc.add(new StoredField("beds", beds));
                    } catch (NumberFormatException e) { }
                }

                // Reviews y calificaciones
                String revs = safeGet(line, idx.get("number_of_reviews"));
                if (revs != null && !revs.isEmpty()) {
                    try {
                        int nrev = Integer.parseInt(revs);
                        doc.add(new IntPoint("number_of_reviews", nrev));
                        doc.add(new StoredField("number_of_reviews", nrev));
                    } catch (NumberFormatException e) { }
                }

                String ratingS = safeGet(line, idx.get("review_scores_rating"));
                if (ratingS != null && !ratingS.isEmpty()) {
                    try {
                        double rating = Double.parseDouble(ratingS);
                        doc.add(new DoublePoint("review_scores_rating", rating));
                        doc.add(new StoredField("review_scores_rating", rating));
                    } catch (NumberFormatException e) { }
                }

                // Disponibilidad
                String availability30 = safeGet(line, idx.get("availability_30"));
                if (availability30 != null && !availability30.isEmpty()) {
                    try {
                        int avail30 = Integer.parseInt(availability30);
                        doc.add(new IntPoint("availability_30", avail30));
                        doc.add(new StoredField("availability_30", avail30));
                    } catch (NumberFormatException e) { }
                }

                // Actualizar documento en el índice
                Term idTerm = new Term("id_str", id);
                writer.updateDocument(idTerm, doc);

                count++;
                if (count % 1000 == 0) System.out.println("Indexed " + count + " properties...");
            }
            System.out.println("Finished indexing properties. Total: " + count);
        }
    }

    /**
     * Indexa hosts desde el CSV con el formato específico
     */
    private static void indexHosts(IndexWriter writer, String csvPath) throws IOException {
        try (CSVReader r = new CSVReader(new FileReader(csvPath))) {
            String[] header = r.readNext();
            if (header == null) throw new IOException("CSV vacío: " + csvPath);
            Map<String, Integer> idx = headerMap(header);

            String[] line;
            int count = 0;
            while ((line = r.readNext()) != null) {
                Document doc = new Document();

                String hostId = safeGet(line, idx.get("host_id"));
                if (hostId == null || hostId.isEmpty()) {
                    System.err.println("Host sin host_id en linea " + (count+2) + " - se ignora");
                    continue;
                }
                doc.add(new StringField("id_str", hostId, Field.Store.YES));

                // Información del host
                String hostName = safeGet(line, idx.get("host_name"));
                if (hostName != null) doc.add(new TextField("host_name", hostName, Field.Store.YES));

                String hostSinceS = safeGet(line, idx.get("host_since"));
                if (hostSinceS != null && !hostSinceS.isEmpty()) {
                    try {
                        Date d = DATE_PARSER.parse(hostSinceS);
                        long epoch = d.getTime();
                        doc.add(new LongPoint("host_since", epoch));
                        doc.add(new StoredField("host_since", epoch));
                        doc.add(new SortedNumericDocValuesField("host_since_sorted", epoch));
                    } catch (ParseException e) {
                        doc.add(new StringField("host_since_raw", hostSinceS, Field.Store.YES));
                    }
                }

                String hostLoc = safeGet(line, idx.get("host_location"));
                if (hostLoc != null) doc.add(new TextField("host_location", hostLoc, Field.Store.YES));

                String hostAbout = safeGet(line, idx.get("host_about"));
                if (hostAbout != null) doc.add(new TextField("host_about", hostAbout, Field.Store.YES));

                String resp = safeGet(line, idx.get("host_response_time"));
                if (resp != null) doc.add(new StringField("host_response_time", resp, Field.Store.YES));

                String superhost = safeGet(line, idx.get("host_is_superhost"));
                if (superhost != null) doc.add(new StringField("host_is_superhost", superhost, Field.Store.YES));

                // Estadísticas del host
                String hostListingsCount = safeGet(line, idx.get("host_listings_count"));
                if (hostListingsCount != null && !hostListingsCount.isEmpty()) {
                    try {
                        int listingsCount = Integer.parseInt(hostListingsCount);
                        doc.add(new IntPoint("host_listings_count", listingsCount));
                        doc.add(new StoredField("host_listings_count", listingsCount));
                    } catch (NumberFormatException e) { }
                }

                // Actualizar documento en el índice
                Term idTerm = new Term("id_str", hostId);
                writer.updateDocument(idTerm, doc);

                count++;
                if (count % 1000 == 0) System.out.println("Indexed " + count + " hosts...");
            }
            System.out.println("Finished indexing hosts. Total: " + count);
        }
    }

    // --------------------
    // Helpers (sin cambios)
    // --------------------

    private static Map<String, Integer> headerMap(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = header[i].trim();
            map.put(key, i);
        }
        return map;
    }

    private static String safeGet(String[] line, Integer idx) {
        if (idx == null) return null;
        if (idx < 0 || idx >= line.length) return null;
        String v = line[idx].trim();
        return v.isEmpty() ? null : v;
    }
}
