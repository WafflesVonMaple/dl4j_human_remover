import org.datavec.image.loader.Java2DNativeImageLoader;
import org.nd4j.linalg.api.ndarray.INDArray;
import spark.Request;
import spark.Response;
import spark.Spark;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RemoveBackgroundWebServer {

    private static final double INPUT_SIZE = 512.0d;
    private final BackgroundRemover b = BackgroundRemover.loadModel("/Users/tonykwok/shadow/experiment/image-background-removal/mobile_net_model/frozen_inference_graph.pb");

    public static void main(String[] args) {
        new RemoveBackgroundWebServer().start();
    }

    private void start() {
        Spark.port(5000);
        Spark.post("/removebg", this::inference);
    }

    private Object inference(Request request, Response response) throws IOException {
        try {
            return doInference(request, response);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private Object doInference(Request request, Response response) throws IOException {
        byte[] body = request.bodyAsBytes();
        System.out.println("Received bytes " + body.length);
        try (InputStream bio = new ByteArrayInputStream(body)) {
            BufferedImage bimg = ImageIO.read(bio);
            int width = bimg.getWidth();
            int height = bimg.getHeight();
            double resizeRatio = INPUT_SIZE / Math.max(width, height);
            Java2DNativeImageLoader l = new Java2DNativeImageLoader((int) (height * resizeRatio), (int) (width * resizeRatio), 3);
            INDArray input = l.asMatrix(bimg).permute(0, 2, 3, 1);
            INDArray mat = b.predict(input);
            BufferedImage bufferedImage = drawSegment(input, mat);
            response.raw().setContentType("image/jpeg");
            try (OutputStream out = response.raw().getOutputStream()) {
                ImageIO.write(bufferedImage, "jpg", out);
            }
        }
        return response;
    }


    private static BufferedImage drawSegment(INDArray baseImg, INDArray matImg) {
        long[] shape = baseImg.shape();

        long height = shape[1];
        long width = shape[2];
        BufferedImage image = new BufferedImage((int) width, (int) height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int mask = matImg.getInt(0, y, x);
                if (mask != 0) {
                    int red = baseImg.getInt(0, y, x, 2);
                    int green = baseImg.getInt(0, y, x, 1);
                    int blue = baseImg.getInt(0, y, x, 0);

                    red = Math.max(Math.min(red, 255), 0);
                    green = Math.max(Math.min(green, 255), 0);
                    blue = Math.max(Math.min(blue, 255), 0);
                    image.setRGB(x, y, new Color(red, green, blue).getRGB());
                }

            }
        }
        return image;
    }

}