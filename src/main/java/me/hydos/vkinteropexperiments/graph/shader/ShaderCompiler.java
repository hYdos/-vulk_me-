package me.hydos.vkinteropexperiments.graph.shader;

import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class ShaderCompiler {
    private static final MessageDigest HASH_ALG;
    private static final Path COMPILED_SHADER_DIR = Paths.get("shaders/cached");
    private static final Path SRC_SHADER_DIR = Paths.get("shaders/src");

    public static byte[] compile(String path, int type) {
        var compiledFileName = toHexString(HASH_ALG.digest(path.getBytes(StandardCharsets.UTF_8))) + ".spirv";
        var compiledPath = COMPILED_SHADER_DIR.resolve(compiledFileName);

        try {
            if (Files.exists(compiledPath)) return Files.readAllBytes(compiledPath);
            else {
                var compiledShader = compileShader(path, Files.readString(SRC_SHADER_DIR.resolve(path), StandardCharsets.UTF_8), type);
                Files.write(compiledPath, compiledShader);
                return compiledShader;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] compileShader(String name, String src, int type) {
        var compiler = Shaderc.shaderc_compiler_initialize();
        var options = Shaderc.shaderc_compile_options_initialize();
        var shaderCType = switch (type){
            case VK10.VK_SHADER_STAGE_VERTEX_BIT -> Shaderc.shaderc_vertex_shader;
            case VK10.VK_SHADER_STAGE_FRAGMENT_BIT -> Shaderc.shaderc_fragment_shader;
            case VK10.VK_SHADER_STAGE_COMPUTE_BIT -> Shaderc.shaderc_compute_shader;
            case VK10.VK_SHADER_STAGE_GEOMETRY_BIT -> Shaderc.shaderc_geometry_shader;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };

        var result = Shaderc.shaderc_compile_into_spv(
                compiler,
                src,
                shaderCType,
                src,
                "main",
                options
        );

        if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success)
            throw new RuntimeException(name + " compilation failed: " + Shaderc.shaderc_result_get_error_message(result));

        var buffer = Shaderc.shaderc_result_get_bytes(result);
        var compiledShader = new byte[buffer.remaining()];
        buffer.get(compiledShader);
        Shaderc.shaderc_compile_options_release(options);
        Shaderc.shaderc_compiler_release(compiler);
        return compiledShader;
    }

    private static String toHexString(byte... bytes) {
        var formatter = new Formatter();
        for (var b : bytes) formatter.format("%02X", b);
        return formatter.toString();
    }

    static {
        try {
            Files.createDirectories(COMPILED_SHADER_DIR);
            Files.createDirectories(SRC_SHADER_DIR);
            HASH_ALG = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
