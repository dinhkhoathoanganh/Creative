package sgraph;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import util.Light;
import util.Material;
import util.PolygonMesh;
import util.TextureImage;

/**
 * Created by ashesh on 4/12/2016.
 */
public class RTScenegraphRenderer implements IScenegraphRenderer {
    private final float shadowFudge = 0.01f;
    private List<Light> lights;
    private Color refractionColor = new Color(0,0,0);;
    /**
     * A map to store all the textures
     */
    private Map<String, TextureImage> textures;

    public RTScenegraphRenderer() {
        textures = new TreeMap<String,TextureImage>();
    }

    @Override
    public void setContext(Object obj) throws IllegalArgumentException {
        throw new IllegalArgumentException("Not valid for this renderer");
    }

    @Override
    public void initShaderProgram(util.ShaderProgram shaderProgram, Map<String, String> shaderVarsToVertexAttribs) {
        throw new IllegalArgumentException("Not valid for this renderer");

    }

    @Override
    public int getShaderLocation(String name) {
        throw new IllegalArgumentException("Not valid for this renderer");

    }

    @Override
    public void addMesh(String name, PolygonMesh mesh) throws Exception {

    }

    public void initLightsInShader(List<Light> lights) {
        throw new IllegalArgumentException("Not valid for this renderer");
    }

    private class RaytraceThread implements Runnable {
        private Stack<Matrix4f> modelView;
        private int winHeight, winWidth;
        private float radianFOVY;
        private INode root;
        private BufferedImage resultImage;
        BlockingQueue<Vector2i> queue;

        public RaytraceThread(BufferedImage resultImage, BlockingQueue<Vector2i> queue) {
            this.resultImage = resultImage;
            this.queue = queue;
        }

        public void reset(Stack<Matrix4f> modelView, INode root,
            int winHeight, int winWidth, float radianFOVY) {
            this.modelView = modelView;
            this.winHeight = winHeight;
            this.winWidth = winWidth;
            this.radianFOVY = radianFOVY;
            this.root = root;
        }

        @Override
        public void run() {
            Vector2i xy = null;
            while((xy = getNextElement()) != null) {
                int i = xy.x;
                int j = xy.y;
                Ray rayView = new Ray();
                rayView.start = new Vector4f(0, 0, 0, 1);
                rayView.direction = new Vector4f(i - 0.5f * winWidth,
                    j - 0.5f * winHeight,
                    -0.5f * winHeight / radianFOVY,
                    0.0f);

                HitRecord hitR = new HitRecord();
                raycast(rayView, root, modelView, hitR);
                Color color = getRaytracedColor(hitR, root, modelView, rayView);
                resultImage.setRGB(i,winHeight-1-j,color.getRGB());
            }
        }

        private Vector2i getNextElement() {
            try {
                return queue.poll(1, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }
    }

    @Override
    public void draw(INode root, Stack<Matrix4f> modelView) {
        int i,j;
        int width = 800;
        int height = 800;
        float FOVY = 120.0f;
        Ray rayView = new Ray();

        this.lights = root.getLightsInView(modelView);

        BufferedImage output = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);

        rayView.start = new Vector4f(0,0,0,1);
        float radianFOVY = (float)Math.tan(Math.toRadians(0.5*FOVY));

        // Create queue with each vector
        long start = System.currentTimeMillis();
        BlockingQueue<Vector2i> queue = new ArrayBlockingQueue<>(width * height);
        for (i = 0; i < width; i++) {
            for (j = 0; j < height; j++) {
                queue.add(new Vector2i(i, j));
            }
        }
        int numThreads = 2;
        RaytraceThread[] raytracers = new RaytraceThread[numThreads];
        Thread[] threads = new Thread[numThreads];
        for (i = 0; i < numThreads; i++) {
            raytracers[i] = new RaytraceThread(output, queue);
            raytracers[i].reset((Stack<Matrix4f>)modelView.clone(), root,
                height, width, radianFOVY);
            threads[i] = new Thread(raytracers[i]);
        }

        for (i = 0; i < numThreads; i++) {
            threads[i].start();
        }
        for (int threadNum = 0; threadNum < numThreads; threadNum++) {
            try {
                threads[threadNum].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
//        for (i=0;i<width;i++)
//        {
//            for (j=0;j<height;j++)
//            {
//                /*
//                 create ray in view coordinates
//                 start point: 0,0,0 always!
//                 going through near plane pixel (i,j)
//                 So 3D location of that pixel in view coordinates is
//                 x = i-width/2
//                 y = j-height/2
//                 z = -0.5*height/tan(FOVY)
//                */
//                rayView.direction = new Vector4f(i-0.5f*width,
//                        j-0.5f*height,
//                        -0.5f*height/radianFOVY,
//                        0.0f);
//
//                HitRecord hitR = new HitRecord();
//                Color color;
//                raycast(rayView,root,modelView,hitR);
//                color = getRaytracedColor(hitR, root, modelView, rayView);
//                output.setRGB(i,height-1-j,color.getRGB());
//            }
//        }
        System.out.println("Took: " + (System.currentTimeMillis() - start));


        OutputStream outStream = null;

        try {
            outStream = new FileOutputStream("output/raytrace.png");
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Could not write raytraced image!");
        }

        try {
            ImageIO.write(output,"png",outStream);
            outStream.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write raytraced image!");
        }

    }

    private void raycast(Ray rayView,INode root,Stack<Matrix4f> modelView,HitRecord hitRecord) {
        root.intersect(rayView,modelView,hitRecord);

    }

    private Color getRaytracedColor(HitRecord hitRecord, INode root, Stack<Matrix4f> modelView, Ray ray) {
        if (hitRecord.intersected())
            return shade(hitRecord.point,hitRecord.normal,hitRecord.material,
                    hitRecord.textureName,hitRecord.texcoord, root, modelView, ray, 0, 1f);
        else
            return new Color(0,0,0);
    }

    private Color shade(Vector4f point, Vector4f normal, Material material,
                        String textureName, Vector2f texcoord,
                        INode root, Stack<Matrix4f> modelView,
                        Ray ray, int bounce, float refractiveIndexStart) {

        Vector3f color = new Vector3f(0,0,0), normal3f = new Vector3f(normal.x, normal.y, normal.z), rayDirection3f = new Vector3f(ray.direction.x, ray.direction.y, ray.direction.z), refractionDirection3f;
        Vector4f reflectionColor4f = new Vector4f(0, 0, 0, 1), refractionColor4f = new Vector4f(0,0,0,1);

        for (int i=0;i<lights.size();i++)
        {
            Vector3f lightVec;
            Vector3f spotdirection = new Vector3f(
                    lights.get(i).getSpotDirection().x,
                    lights.get(i).getSpotDirection().y,
                    lights.get(i).getSpotDirection().z);


            if (spotdirection.length()>0)
                spotdirection = spotdirection.normalize();

            if (lights.get(i).getPosition().w!=0) {
                lightVec = new Vector3f(
                        lights.get(i).getPosition().x - point.x,
                        lights.get(i).getPosition().y - point.y,
                        lights.get(i).getPosition().z - point.z);
            }
            else
            {
                lightVec = new Vector3f(
                        -lights.get(i).getPosition().x,
                        -lights.get(i).getPosition().y,
                        -lights.get(i).getPosition().z);
            }
            lightVec = lightVec.normalize();

            Ray shadowRay = new Ray();
            shadowRay.start = new Vector4f(point).add(new Vector4f(lightVec, 0).mul(shadowFudge));
            shadowRay.direction = new Vector4f(lightVec, 0);
            HitRecord hitR = new HitRecord();
            raycast(shadowRay, root, modelView, hitR);
            if (!(!hitR.intersected() || hitR.time < 1)) {
                continue;
            }


            /* if point is not in the light cone of this light, move on to next light */
            if (new Vector3f(lightVec).negate().dot(spotdirection)<=Math.cos(Math.toRadians(lights.get(i).getSpotCutoff())))
                continue;


            Vector3f normalView = new Vector3f(normal.x,normal.y,normal.z).normalize();

            float nDotL = normalView.dot(lightVec);

            Vector3f viewVec = new Vector3f(point.x,point.y,point.z).negate();
            viewVec = viewVec.normalize();

            Vector3f reflectVec = new Vector3f(lightVec).negate().reflect(normalView);
            reflectVec = reflectVec.normalize();

            float rDotV = Math.max(reflectVec.dot(viewVec),0.0f);

            Vector3f ambient = new Vector3f(
                    material.getAmbient().x * lights.get(i).getAmbient().x,
                    material.getAmbient().y * lights.get(i).getAmbient().y,
                    material.getAmbient().z * lights.get(i).getAmbient().z);

            Vector3f diffuse = new Vector3f(
                    material.getDiffuse().x * lights.get(i).getDiffuse().x * Math.max(nDotL,0),
                    material.getDiffuse().y * lights.get(i).getDiffuse().y * Math.max(nDotL,0),
                    material.getDiffuse().z * lights.get(i).getDiffuse().z * Math.max(nDotL,0));
            Vector3f specular;
            if (nDotL>0) {
                specular = new Vector3f(
                        material.getSpecular().x * lights.get(i).getSpecular().x * (float) Math.pow(rDotV, material.getShininess()),
                        material.getSpecular().y * lights.get(i).getSpecular().y * (float) Math.pow(rDotV, material.getShininess()),
                        material.getSpecular().z * lights.get(i).getSpecular().z * (float) Math.pow(rDotV, material.getShininess()));
            }
            else
            {
                specular = new Vector3f(0,0,0);
            }
            color = new Vector3f(color).add(ambient).add(diffuse).add(specular);
        }

        if (textures.containsKey(textureName)) {
            Vector4f colorFromTexture = textures.get(textureName).getColor(texcoord.x, 1 - texcoord.y);
            color = color.mul(colorFromTexture.x, colorFromTexture.y, colorFromTexture.z);
        }


        // check validity of 3 measurements

        float absorption = material.getAbsorption(), reflection = material.getReflection(), transparency = material.getTransparency(), refractiveIndex = material.getRefractiveIndex();

        absorption = (absorption < 0 || absorption >1) ? 1 : absorption; // make sure in between 0 and 1
        reflection = (reflection < 0 || reflection >1) ? 0 : reflection; // make sure in between 0 and 1
        transparency = (transparency < 0 || transparency >1) ? 0 : transparency; // make sure in between 0 and 1

        float sum = absorption+reflection+transparency;
        if (absorption+reflection+transparency !=1){
            absorption /= sum;
            reflection /= sum;
            transparency /= sum;
        }

        color = color.mul(absorption);

        // Reflection
        // if material is reflective
        if (reflection != 0) {

            Vector4f reflectRay = new Vector4f(new Vector3f(new Vector3f(ray.direction.x, ray.direction.y, ray.direction.z))
                    .reflect(normal.x, normal.y, normal.z), 0);

            // get reflection
            Ray resultRay = new Ray();
            resultRay.start = point.add(new Vector4f(reflectRay).mul(0.01f));
            resultRay.direction = reflectRay;

            rayCastBounce(resultRay, modelView, reflectionColor4f, bounce, reflection, root, refractiveIndexStart);
        }
        color = color.add(new Vector3f(reflectionColor4f.x, reflectionColor4f.y, reflectionColor4f.z));

        // Refraction

        // if material has transparent index
        if (transparency !=0) {

            float snellRatio = 1f/refractiveIndex; // start from vacuum (default)
            if (Math.abs(refractiveIndexStart - 1f) > 0.0001f) { // start from vacuum (default)
                snellRatio = refractiveIndex;
                normal.negate();
            }

            // calculate cosine theta i and r
            float cosThetaI = -normal3f.dot(rayDirection3f);
            float cosThetaRSquared = 1f-(snellRatio*snellRatio*(1f-cosThetaI*cosThetaI));
            
            if (cosThetaRSquared>=0.0f) { // check for invalid value
                float cosThetaR = (float) Math.sqrt(cosThetaRSquared);

                // calculate refraction direction
                Vector4f refractionDirection = new Vector4f((new Vector3f(normal3f).mul(cosThetaI)
                        .add(rayDirection3f)).mul(snellRatio)
                        .sub(new Vector3f(normal3f).mul(cosThetaR))
                        , 0).normalize();
                
                Ray outRay = new Ray();
                outRay.start = point.add(new Vector4f(refractionDirection).mul(0.01f));
                outRay.direction = refractionDirection;

                // recursion call
                refractionRayCast(outRay, root, modelView, bounce, refractiveIndexStart);

                refractionColor4f = new Vector4f(((float) this.refractionColor.getRed()/255)*transparency,
                        ((float) this.refractionColor.getGreen()/255)*transparency, ((float) this.refractionColor.getBlue()/255)*transparency, 1);
            }

        }
        color = color.add(new Vector3f(refractionColor4f.x, refractionColor4f.y, refractionColor4f.z));



        color.x = Math.min(color.x,1);
        color.y = Math.min(color.y,1);
        color.z = Math.min(color.z,1);

        return new Color((int)(255*color.x),(int)(255*color.y),(int)(255*color.z));
    }


    private void rayCastBounce(Ray resultRay, Stack<Matrix4f>stack, Vector4f reflectionColor4f,int bounce, float reflection, INode root, float refractiveIndexStart) {

        // end recursion if bounce more than 5
        if (bounce >5) return;

        HitRecord hitRecord = new HitRecord();
        root.intersect(resultRay, stack, hitRecord);

        if (hitRecord.intersected()) {
            bounce++;
            Color color = shade(hitRecord.point, hitRecord.normal, hitRecord.material, hitRecord.textureName, hitRecord.texcoord, root, stack, resultRay, bounce, refractiveIndexStart);
            reflectionColor4f.add(((float) color.getRed()/255)*reflection, ((float) color.getGreen()/255)*reflection,
                    ((float) color.getBlue()/255)*reflection, 0);
        }
    }


    private void refractionRayCast(Ray rayView,INode root, Stack<Matrix4f> modelView, int bounce, float refractiveIndexStart) {
        // end recursion if bounce more than 5
        if (bounce > 5) return;
        bounce++;

        // check if intersect
        HitRecord hitRecord = new HitRecord();
        raycast(rayView,root,modelView,hitRecord);

        this.refractionColor = (hitRecord.intersected())
                    ? shade(hitRecord.point,hitRecord.normal,hitRecord.material, hitRecord.textureName, hitRecord.texcoord,
                    root, modelView, rayView, bounce, refractiveIndexStart)
                    : new Color(0,0,0);
    }

    @Override
    public void drawMesh(String name, Material material, String textureName, Matrix4f transformation) {
        throw new IllegalArgumentException("Not valid for this renderer");
    }

    @Override
    public void addTexture(String name,String path)
    {
        TextureImage image = null;
        String imageFormat = path.substring(path.indexOf('.')+1);
        try {
            image = new TextureImage(path,imageFormat,name);
        } catch (IOException e) {
            throw new IllegalArgumentException("Texture "+path+" cannot be read!");
        }
        textures.put(name,image);
    }

    @Override
    public void dispose() {

    }
}
