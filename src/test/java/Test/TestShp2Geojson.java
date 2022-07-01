package Test;


import org.junit.Test;
import io.github.homxuwang.util.FileFormat;

import java.util.Map;

public class TestShp2Geojson {

    private String shpPath = "C:\\\\Users\\\\Administrator\\\\Desktop\\\\testdata\\\\22line\\\\line.shp";
    private String outPath = "C:\\Users\\Administrator\\Desktop\\testdata\\22line\\output1.json";
    private FileFormat fileFormat = new FileFormat();
    @Test
    public void testShp2Geojson(){

       Map result =  fileFormat.shape2Geojson(shpPath,outPath);
       System.out.print(result.toString());
    }

}
