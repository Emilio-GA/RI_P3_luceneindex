import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


// Compilar
// javac -cp "lucene-10.3.1/modules/*:lucene-10.3.1/modules-thirdparty/*" /home/felipe/Documents/1ercuatri2025-26/RI/P3/IndiceSimple.java


// Ejecutar
// java -cp ".:lucene-10.3.1/modules/*:lucene-10.3.1/modules-thirdparty/*:/home/felipe/Documents/1ercuatri2025-26/RI/P3" IndiceSimple

public class IndiceSimple {

    private final String indexPath;
    private final String docPath;
    private final boolean create;
    private IndexWriter writer;

    public IndiceSimple(String indexPath, String docPath, boolean create) {
        this.indexPath = indexPath;
        this.docPath = docPath;
        this.create = create;
    }

    public static void main(String[] args) throws IOException {
        // Analizador a utilizar
        Analyzer analyzer = new StandardAnalyzer();
        // Medida de similitud (modelo de recuperación) por defecto BM25; aquí usamos Classic (TF-IDF)
        Similarity similarity = new ClassicSimilarity();

        // Llamada al constructor con los parámetros
        IndiceSimple baseline = new IndiceSimple("./index", "./DataSet", true);

        // Creamos el índice
        baseline.configurarIndice(analyzer, similarity);
        // Insertar los documentos
        baseline.indexarDocumentos();
        // Cerramos el índice
        baseline.cerrar();
    }

    public void configurarIndice(Analyzer analyzer, Similarity similarity) throws IOException {
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setSimilarity(similarity);
        // Crear un nuevo índice cada vez que se ejecute
        // Para insertar documentos a un índice existente
        // iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        iwc.setOpenMode(create ? IndexWriterConfig.OpenMode.CREATE : IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        // Localización del índice
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        // Creamos el índice
        writer = new IndexWriter(dir, iwc);
    }

    public void indexarDocumentos() throws IOException {
        Path datasetDir = Paths.get(docPath);
        if (!Files.exists(datasetDir)) {
            throw new IOException("Ruta de documentos no existe: " + datasetDir.toAbsolutePath());
        }

        if (Files.isDirectory(datasetDir)) {
            // Para cada uno de los documentos a insertar
            // Indexar todos los ficheros de texto bajo la carpeta
            try (var paths = Files.walk(datasetDir)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        indexarDocumentoDesdeArchivo(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } else {
            // Si es un fichero único, lo procesamos igualmente
            indexarDocumentoDesdeArchivo(datasetDir);
        }
    }

    private void indexarDocumentoDesdeArchivo(Path path) throws IOException {
        // Leemos el documento sobre un string
        List<String> lineas = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String linea : lineas) {
            String trimmed = linea.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // cadena <-- "134,hola esto es un ejemplo...", 233 (ejemplo)
            // Esperado: "123,contenido del documento ..."
            // Si el formato difiere, ajusta el parser aquí.
            int coma = trimmed.indexOf(',');
            if (coma <= 0 || coma == trimmed.length() - 1) {
                // Si no hay coma, intentamos asignar un ID sintético y usar toda la línea como cuerpo
                addDocumento(null, trimmed);
            } else {
                // Obtener campo entero de cadena
                String idStr = trimmed.substring(0, coma).trim();
                String cuerpo = trimmed.substring(coma + 1).trim();
                Integer id = parseEnteroSeguro(idStr);
                addDocumento(id, cuerpo);
            }
        }
    }

    private void addDocumento(Integer id, String cuerpo) throws IOException {
        // Creamos el documento Lucene
        Document doc = new Document();

        if (id != null) {
            // Almacenamos el campo ID en el documento Lucene
            doc.add(new IntPoint("ID", id));
            doc.add(new StoredField("ID", id));
        }

        if (cuerpo != null && !cuerpo.isEmpty()) {
            // Almacenamos el campo texto en el documento Lucene
            doc.add(new TextField("Body", cuerpo, Field.Store.YES));
        }

        // Obtenemos los siguientes campos
        // ...
        // Insertamos el documento Lucene en el índice
        writer.addDocument(doc);
        // Si lo que queremos es actualizar el documento
        // writer.updateDocument(new Term("ID", valor.toString()), doc);
    }

    private static Integer parseEnteroSeguro(String s) {
        try {
            return Integer.decode(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void cerrar() {
        try {
            if (writer != null) {
                writer.commit();
                writer.close();
            }
        } catch (IOException e) {
            System.out.println("Error cerrando el índice.");
        }
    }
}


