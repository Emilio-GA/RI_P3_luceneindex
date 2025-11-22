import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Clase básica de búsqueda Lucene para el índice de Airbnb
 * 
 * Busca en el índice de propiedades (index_properties) creado por AirbnbIndexador.
 * 
 * COMPILACIÓN:
 *   javac -cp "lucene-10.3.1/modules/*:lucene-10.3.1/modules-thirdparty/*:lib/*" BusquedasLucene.java
 * 
 * EJECUCIÓN:
 *   java -cp ".:lucene-10.3.1/modules/*:lucene-10.3.1/modules-thirdparty/*:lib/*" BusquedasLucene --index-root ./index_root
 */
public class BusquedasLucene {

    // Ubicación del índice (por defecto index_root/index_properties)
    private String indexPath;

    public BusquedasLucene(String indexRoot) {
        this.indexPath = Paths.get(indexRoot, "index_properties").toString();
    }

    public static void main(String[] args) {
        String indexRoot = "./index_root";
        
        // Parsear argumentos simples
        for (int i = 0; i < args.length; i++) {
            if ("--index-root".equals(args[i]) && i + 1 < args.length) {
                indexRoot = args[i + 1];
                i++;
            }
        }

        BusquedasLucene busqueda = new BusquedasLucene(indexRoot);
        
        Analyzer analyzer = crearAnalizador();
        
        Similarity similarity = new ClassicSimilarity();
        // ===================================
        // Otras alternativas implementadas en Lucene
        // Similarity similarity = new BM25Similarity(); // Valor por defecto en Lucene
        // Similarity similarity = new LMDirichletSimilarity();
        
        // Búsqueda en el índice
        busqueda.indexSearch(analyzer, similarity);
    }

    /**
     * Crea el analizador con la misma configuración que AirbnbIndexador
     */
    private static Analyzer crearAnalizador() {
        Analyzer defaultAnalyzer = new StandardAnalyzer();
        java.util.Map<String, Analyzer> perField = new java.util.HashMap<>();
        
        // Campos en inglés
        perField.put("description", new EnglishAnalyzer());
        perField.put("neighborhood_overview", new EnglishAnalyzer());
        
        // Analyzer personalizado para campos categóricos: keyword + lowercase
        Analyzer lowercaseKeywordAnalyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                KeywordTokenizer tokenizer = new KeywordTokenizer();
                TokenStream filter = new LowerCaseFilter(tokenizer);
                return new TokenStreamComponents(tokenizer, filter);
            }
        };
        
        // Campos categóricos (keyword + lowercase para case-insensitive)
        perField.put("neighbourhood_cleansed", lowercaseKeywordAnalyzer);
        perField.put("property_type", lowercaseKeywordAnalyzer);
        
        return new PerFieldAnalyzerWrapper(defaultAnalyzer, perField);
    }

    // ====================================================
    // Asumimos que en el índice hay campos como:
    // - name: TextField, almacenado
    // - description: TextField, almacenado
    // - id: IntPoint, almacenado como StoredField
    // - price: DoublePoint, almacenado
    // - review_scores_rating: DoublePoint, almacenado
    // - property_type: StringField, almacenado
    // - neighbourhood_cleansed: StringField, almacenado

    public void indexSearch(Analyzer analyzer, Similarity similarity) {
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
            IndexSearcher searcher = new IndexSearcher(reader);
            // Asignamos cómo se calcula la similitud entre documentos
            searcher.setSimilarity(similarity);

            BufferedReader in = null;
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            // El campo description será analizado utilizando el analyzer
            // También podemos buscar en name, neighborhood_overview, etc.
            QueryParser parser = new QueryParser("description", analyzer);
            
            // Permitir búsqueda en múltiples campos
            // QueryParser parser = new MultiFieldQueryParser(
            //     new String[]{"name", "description", "neighborhood_overview"}, analyzer);

            while (true) {
                System.out.println("Consulta?: ");

                String line = in.readLine();

                if (line == null || line.length() == -1) {
                    break;
                }
                // Eliminamos caracteres blancos al inicio y al final
                line = line.trim();
                if (line.length() == 0) {
                    break;
                }

                Query query;
                try {
                    query = parser.parse(line);

                } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                    System.out.println("Error en cadena consulta.");
                    continue;
                }

                TopDocs hits = searcher.search(query, 100);

                System.out.println("Docs encontrados: " + hits.totalHits.value());
                for (ScoreDoc hit : hits.scoreDocs) {
                    Document doc = searcher.storedFields().document(hit.doc);
                    
                    // Mostrar campos relevantes
                    // Nota: id está indexado como IntPoint pero no almacenado, 
                    // así que no podemos recuperarlo directamente
                    String name = doc.get("name");
                    String description = doc.get("description");
                    String propertyType = doc.get("property_type");
                    String neighbourhood = doc.get("neighbourhood_cleansed");
                    
                    // Campos numéricos almacenados como StoredField
                    Double price = null;
                    Double rating = null;
                    try {
                        if (doc.getField("price") != null) {
                            price = doc.getField("price").numericValue().doubleValue();
                        }
                        if (doc.getField("review_scores_rating") != null) {
                            rating = doc.getField("review_scores_rating").numericValue().doubleValue();
                        }
                    } catch (Exception e) {
                        // Ignorar si el campo no está disponible
                    }
                    
                    System.out.println("------------------------------------");
                    System.out.println("Doc ID (Lucene): " + hit.doc);
                    if (name != null) {
                        System.out.println("Nombre: " + name);
                    }
                    if (propertyType != null) {
                        System.out.println("Tipo: " + propertyType);
                    }
                    if (neighbourhood != null) {
                        System.out.println("Barrio: " + neighbourhood);
                    }
                    if (price != null) {
                        System.out.println("Precio: $" + String.format("%.2f", price));
                    }
                    if (rating != null) {
                        System.out.println("Rating: " + String.format("%.1f", rating));
                    }
                    if (description != null && description.length() > 0) {
                        // Mostrar solo los primeros 200 caracteres de la descripción
                        String descShort = description.length() > 200 ? 
                            description.substring(0, 200) + "..." : description;
                        System.out.println("Descripción: " + descShort);
                    }
                    System.out.println("Score: " + hit.score);
                    System.out.println();
                }

                if (line.equals("")) {
                    break;
                }
            }
            reader.close();
        } catch (IOException e) {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
    }
}

