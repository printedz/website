package org.printed.chat.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.printed.chat.model.BlogPost;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BlogService {

    private final Map<String, BlogPost> blogPosts = new HashMap<>();
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public BlogService() {
        loadBlogPosts();
    }

    private void loadBlogPosts() {
        try {
            // Cargar post1.md
            loadPost("1", "blog/post1.md");

            // Cargar post2.md
            loadPost("2", "blog/post2.md");

            // Cargar post3.md
            loadPost("3", "blog/post3.md");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPost(String id, String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        InputStream inputStream = resource.getInputStream();
        String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        // Extraer título
        String title = extractTitle(markdown);

        // Extraer fecha de publicación
        LocalDate publishDate = extractPublishDate(markdown);

        // Generar vista previa (primeros dos párrafos)
        String preview = generatePreview(markdown);

        // Convertir Markdown a HTML
        Node document = parser.parse(markdown);
        String htmlContent = renderer.render(document);

        // Crear y almacenar el post
        BlogPost post = new BlogPost(id, title, publishDate, preview, htmlContent);
        blogPosts.put(id, post);
    }

    private String extractTitle(String markdown) {
        Pattern pattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        return matcher.find() ? matcher.group(1) : "Sin título";
    }

    private LocalDate extractPublishDate(String markdown) {
        Pattern pattern = Pattern.compile("\\*Publicado:\\s+(.+)\\*", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        if (matcher.find()) {
            try {
                // Modificar el patrón para que coincida exactamente con el formato esperado
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d 'de' MMMM',' yyyy");
                return LocalDate.parse(matcher.group(1), formatter);
            } catch (Exception e) {
                e.printStackTrace(); // Para ver el error exacto en los logs
                return LocalDate.now();
            }
        }
        return LocalDate.now();
    }

    private String generatePreview(String markdown) {
        Pattern pattern = Pattern.compile("^(?!#)(.+?)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(markdown);
        StringBuilder preview = new StringBuilder();
        int paragraphCount = 0;

        while (matcher.find() && paragraphCount < 2) {
            String paragraph = matcher.group(1).trim();
            if (!paragraph.isEmpty() && !paragraph.startsWith("*Publicado")) {
                // Convertir el párrafo a HTML
                Node node = parser.parse(paragraph);
                preview.append(renderer.render(node));
                paragraphCount++;
            }
        }

        return preview.toString();
    }

    public List<BlogPost> getAllPosts() {
        return new ArrayList<>(blogPosts.values());
    }

    public BlogPost getPostById(String id) {
        return blogPosts.get(id);
    }
}