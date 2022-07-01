import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.geojson.geom.GeometryJSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.feature.AttributeTypeBuilder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.type.AttributeTypeImpl;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.SimpleInternationalString;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * @author Administrator
 */
public class FileFormat {
    public static Map geojson2Shape(String jsonPath, String shpPath) {
        Map map = new HashMap();
        GeometryJSON gjson = new GeometryJSON();
        try {
            String strJson = readFileContent(jsonPath);
            JSONObject json = JSON.parseObject(strJson,Feature.IgnoreNotMatch);
            JSONArray features = json.getJSONArray("features");
            JSONObject feature0 = JSON.parseObject(features.getString(0),Feature.InitStringFieldAsEmpty);
            String strType = (feature0.getJSONObject("geometry")).getString("type");
            Map<String, Object> properties0 = (Map<String, Object>) feature0.getJSONObject("properties");

            Class<?> geoType = null;
            switch (strType) {
                case "Point":
                    geoType = Point.class;
                    break;
                case "MultiPoint":
                    geoType = MultiPoint.class;
                    break;
                case "LineString":
                    geoType = LineString.class;
                    break;
                case "MultiLineString":
                    geoType = MultiLineString.class;
                    break;
                case "Polygon":
                    geoType = Polygon.class;
                    break;
                case "MultiPolygon":
                    geoType = MultiPolygon.class;
                    break;
            }
            //创建shape文件对象
            File file = new File(shpPath);
            Map<String, Serializable> params = new HashMap<>();
            params.put(ShapefileDataStoreFactory.URLP.key, file.toURI().toURL());
            ShapefileDataStore ds = (ShapefileDataStore) new ShapefileDataStoreFactory().createNewDataStore(params);
            //设置编码
            Charset charset = Charset.forName("GBK");
            ds.setCharset(charset);

            //定义图形信息和属性信息
            SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
            tb.setCRS(DefaultGeographicCRS.WGS84);
            tb.setName("shapefile");
            tb.add("the_geom", geoType);
            tb.add("gid", Long.class);

            List<AttributeDescriptor> attributeDesList=new ArrayList<>();
            for (Entry<String, Object> entry : properties0.entrySet()) {
                //属性构造器
                AttributeTypeBuilder build = new AttributeTypeBuilder();
                String name=entry.getKey();
                build.setName(name);
                build.setBinding(entry.getValue().getClass());
                build.setDescription(name);
                AttributeTypeImpl type = new AttributeTypeImpl(new NameImpl(name), entry.getValue().getClass(),
                        true, true, null, build.buildType(),new SimpleInternationalString(name));
                String desString=type.getDescription().toString();
                AttributeDescriptor descriptor= build.buildDescriptor(name,type);
                attributeDesList.add(descriptor);
            }
            tb.addAll(attributeDesList);

            ds.createSchema(tb.buildFeatureType());


            try ( //设置Writer
                  FeatureWriter<SimpleFeatureType, SimpleFeature> writer = ds.getFeatureWriter(ds.getTypeNames()[0], Transaction.AUTO_COMMIT)) {
                int index = 0;
                System.out.println(index);
                for (Iterator iterator = features.iterator(); iterator.hasNext();) {
                    JSONObject feature = (JSONObject) iterator.next();
                    Map<String, Object> properties = (Map<String, Object>) feature.getJSONObject("properties");
                    String strFeature = feature.toString();
                    System.out.println(strFeature);
                    Geometry geometry = gjson.read(strFeature);
                    System.out.println(geometry);
                    SimpleFeature simpleFeature = writer.next();
                    //System.out.println(feature);
                    simpleFeature.setAttribute("the_geom", geometry);
                    simpleFeature.setAttribute("gid", index);
                    for (Entry<String, Object> entry : properties.entrySet()) {
                        //simpleFeature.setAttribute(entry.getKey(), entry.getValue());
                        simpleFeature.setAttribute(entry.getKey(), entry.getValue());
                    }
                    writer.write();
                    index++;
                }
            }
            ds.dispose();
            map.put("status", "success");
            map.put("message", shpPath);
        } catch (Exception e) {
            map.put("status", "failure");
            map.put("message", e.getMessage());
            e.printStackTrace();
        }
        return map;
    }

    /**
     * shp转换为Geojson
     *
     * @param shpPath
     * @param jsonPath
     * @return
     */
    public static Map shape2Geojson(String shpPath, String jsonPath) {
        Map map = new HashMap();

        FeatureJSON fjson = new FeatureJSON();

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\": \"FeatureCollection\",\"features\": ");

            File file = new File(shpPath);

            ShapefileDataStore shpDataStore = new ShapefileDataStore(file.toURI().toURL());
            SimpleFeatureType featureType=shpDataStore.getSchema();
            CoordinateReferenceSystem crs=featureType.getCoordinateReferenceSystem();
            //CoordinateReferenceSystem crs_4490 = CRS.decode("EPSG:4490");
            if(crs.toWKT().startsWith(("PROJCS"))){
                System.out.println("该数据坐标系为投影坐标系，不符合入库条件");
            }else if(crs.toWKT().startsWith(("GEOGCS"))){
                if(crs.toWKT().contains("CGCS2000") || crs.toWKT().contains("2000")){
                    System.out.println("该数据坐标系是EPSG:4490，符合入库条件");
                }else{
                    System.out.println("该数据坐标系为投影坐标系，不符合入库条件");
                }
            }

            //设置编码
            Charset charset = Charset.forName("GBK");
            //shpDataStore.setCharset(charset);
            String typeName = shpDataStore.getTypeNames()[0];
            JSONArray array;


            FeatureSource<SimpleFeatureType, SimpleFeature> source = shpDataStore
                    .getFeatureSource(typeName);
            FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures();

            FeatureIterator<SimpleFeature> features = collection.features();

            array = new JSONArray();
            while (features.hasNext()) {
                SimpleFeature feature = features.next();
                Geometry geometry= (Geometry)feature.getAttribute("the_geom"); //(Geometry)feature.getDefaultGeometry();
                StringWriter writer = new StringWriter();
                fjson.writeFeature(feature, writer);
                JSONObject json = JSON.parseObject(writer.toString());
                array.add(json);
                System.out.print(feature.getID());
                System.out.print(": ");
                System.out.println(feature.getDefaultGeometryProperty().getValue());//此行输出的空间信息的wkt表达形式
            }
            sb.append(new String(array.toString().getBytes("ISO-8859-1"), "GBK"));
            sb.append("}");

            //写入文件
            append2File(jsonPath, sb.toString());

            map.put("status", "success");
            map.put("message", sb.toString());
        }catch(Exception e){
            map.put("status", "failure");
            map.put("message", e.getMessage());
            e.printStackTrace();
        }

        return map;
    }

    public static void append2File(String JsonPath, String messsage) {
        try {
            File f = new File(JsonPath);//向指定文本框内写入
            FileWriter fw = new FileWriter(f);
            fw.write(new String(messsage.getBytes(),"GBK"));
            fw.close();
        } catch (Exception e) {}
    }

    public static String readFileContent(String fileName) {
        File file = new File(fileName);
        BufferedReader reader = null;
        StringBuffer sbf = new StringBuffer();
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            String tempStr;
            while ((tempStr = reader.readLine()) != null) {
                sbf.append(tempStr);
            }
            reader.close();
            return sbf.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return sbf.toString();
    }
}