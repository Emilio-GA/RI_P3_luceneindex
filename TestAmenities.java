import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Document;
import java.nio.file.Paths;

/**
 * Script rápido para verificar cómo se indexaron las amenidades
 */
public class TestAmenities {
    public static void main(String[] args) throws Exception {
        String indexPath = args.length > 0 ? args[0] : "index_root/index_properties";
        
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexReader reader = DirectoryReader.open(dir);
        
        System.out.println("=== Información del Índice ===");
        System.out.println("Total documentos: " + reader.numDocs());
        System.out.println();
        
        // Verificar campos
        LeafReader leafReader = reader.leaves().get(0).reader();
        
        System.out.println("=== Campos de Amenidades ===");
        boolean hasAmenity = false;
        
        // Verificar términos para detectar campos
        Terms amenityTerms = leafReader.terms("amenity");
        
        hasAmenity = (amenityTerms != null);
        
        System.out.println("Campo 'amenity' existe: " + hasAmenity);
        System.out.println();
        
        // Ver primer documento
        System.out.println("=== Primer Documento (ID=0) ===");
        Document doc = leafReader.storedFields().document(0);
        
        String[] amenityValues = doc.getValues("amenity");
        System.out.println("Número de valores en 'amenity': " + amenityValues.length);
        System.out.println("Primeras 10 amenidades:");
        for (int i = 0; i < Math.min(10, amenityValues.length); i++) {
            System.out.println("  " + (i+1) + ". " + amenityValues[i]);
        }
        
        // Ver términos indexados
        System.out.println("\n=== Términos Indexados (campo 'amenity') ===");
        Terms terms = leafReader.terms("amenity");
        if (terms != null) {
            TermsEnum termsEnum = terms.iterator();
            System.out.println("Primeros 30 términos indexados (alfabéticos):");
            int count = 0;
            int alphaCount = 0;
            while (termsEnum.next() != null && alphaCount < 30) {
                String term = termsEnum.term().utf8ToString();
                // Mostrar solo términos alfabéticos para ver mejor
                if (term.matches(".*[a-zA-Z].*")) {
                    System.out.println("  " + term + " (freq: " + termsEnum.docFreq() + ")");
                    alphaCount++;
                }
                count++;
                if (count > 1000) break; // Limitar búsqueda
            }
            System.out.println("\nTotal términos revisados: " + count);
        } else {
            System.out.println("No hay términos indexados en 'amenity'");
        }
        
        // Verificar búsqueda de ejemplo
        System.out.println("\n=== Verificación de Búsqueda ===");
        Terms terms2 = leafReader.terms("amenity");
        if (terms2 != null) {
            TermsEnum termsEnum2 = terms2.iterator();
            boolean foundWifi = false;
            boolean foundPool = false;
            boolean foundParking = false;
            
            while (termsEnum2.next() != null) {
                String term = termsEnum2.term().utf8ToString();
                if (term.contains("wifi")) foundWifi = true;
                if (term.contains("pool")) foundPool = true;
                if (term.contains("parking")) foundParking = true;
            }
            
            System.out.println("Término 'wifi' encontrado: " + foundWifi);
            System.out.println("Término 'pool' encontrado: " + foundPool);
            System.out.println("Término 'parking' encontrado: " + foundParking);
        }
        
        reader.close();
        dir.close();
    }
}

