package com.rts.service.map;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import com.rts.dto.AstNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RMSParser {

    private static final Pattern SECTION_PATTERN = Pattern.compile("^<\\s*(\\w+)\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_PATTERN = Pattern.compile("^([a-zA-Z0-9_]+)(?:\\s+([A-Z0-9_]+))?\\s*(\\{)?");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("//.*$");
    private static final Pattern PARAM_PATTERN = Pattern.compile("^([a-zA-Z_]\\w*)\\s+(.+)$");
    /**
     * Parse RMS script text int list of AST nods
     *
     * @param rmsText raw RMS Script Text
     * @return List of parsed AST nodes
     */
    public List<AstNode> parse(String rmsText){

        log.debug("Parsing RMS script ({} chars)", rmsText.length());

        // First, remove all /* */ block comments from the entire script
        String cleanedText = removeBlockComments(rmsText);
        log.debug("After removing block comments: {} chars", cleanedText.length());

        List<AstNode> ast = new ArrayList<>();
        String[] lines = cleanedText.replace("\r","").split("\n");

        int i=0;
        int lineNumber = 0;
        while(i < lines.length){
            lineNumber++;
            String originalLine = lines[i].trim();


            String line = COMMENT_PATTERN.matcher(originalLine).replaceFirst("").trim();

            if( line.isEmpty()){
                log.trace("Empty line at line number {}",i);
                i++;
                continue;
            }

            try{

                
                Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
                if(sectionMatcher.find()){
                    String sectionName = sectionMatcher.group(1).toUpperCase();
                    log.trace("Found section: {}", sectionName);
                    i++;
                    continue;
                }

                Matcher commandMatcher = COMMAND_PATTERN.matcher(line);
                if(commandMatcher.find()){
                    String commandName = commandMatcher.group(1);
                    String imediateArg = commandMatcher.group(2);
                    boolean hasBlock = commandMatcher.group(3) != null;

                    log.trace("Parsing command: {} arg:{} hasBlock: {}", commandName, imediateArg, hasBlock);

                    AstNode node = new AstNode(commandName);

                    if( imediateArg != null){
                        node.addAttribute("_arg", imediateArg);
                    }

                    List<String> blockcontent = null;
                    // Check if brace is on same line or next line
                    if( hasBlock){
                        blockcontent = parseBlock(lines, i, lineNumber);
                        i+= blockcontent.size()+1;

                        for(String blockLine: blockcontent){
                            parseAttribute(blockLine, node, lineNumber);
                        }
                    }
                    else if (i + 1 < lines.length && lines[i + 1].trim().equals("{")) {
                        // Opening brace on next line
                        blockcontent = parseBlock(lines, i + 1, lineNumber + 1);
                        i += blockcontent.size() + 2; // Skip command line, brace line, and block content

                        for(String blockLine: blockcontent){
                            parseAttribute(blockLine, node, lineNumber);
                        }
                    }
                    else{
                        i++;
                    }

                    ast.add(node);
                    log.trace("Added AST node: {} with {} attributes", commandName, node.getAttributes().size());

                    continue;
                }

                if( line.equals("}")){
                    i++;
                    continue;
                }

                Matcher paramMatcher = PARAM_PATTERN.matcher(line);
                if( paramMatcher.find()){
                    String key = paramMatcher.group(1);
                    String value = paramMatcher.group(2);
                    i++;
                    continue;
                }
                throw new IllegalArgumentException("Invalid syntax: "+line);
                

            }catch(Exception e){
                throw new IllegalArgumentException("Line "+ lineNumber +": "+e.getMessage(), e);
            }

        }

        log.info("Successfully parsed {} AST nodes", ast.size());
        return ast;

    }


    /**
     * Parse a single attribute line and add to the ATS node
     * 
     * supports formats:
     * -key value ( e.g. "terrain_type GRASS")
     * -key (boolean flag e.g. "set_tight_grouping")
     * -key value1 value 2 .. (arrrya, e.g "land_id 1 2 3")
     * @param line
     * @param node
     * @param lineNumber
     */
    private void parseAttribute(String line, AstNode node, int lineNumber) {
       // Remove any remaining inline comments and trim
       line = line.trim();
       if (line.isEmpty()) {
           return;
       }

       String[] parts = line.split("\\s+");
       if(parts.length ==  0 || parts[0].isEmpty()){
        return;
       }

       String key = parts[0];

       // Additional safety check for null or empty key
       if (key == null || key.isEmpty()) {
           return;
       }

       if(parts.length ==1){
        // Boolean flag attributes e.g set_tight_grouping
        node.addAttribute(key,true);
        log.trace(" Attribute:{} = true ( flag)", key);
       }else if ( parts.length ==2){
        Object value = parseValue (parts[1]);
        node.addAttribute(key,value);
        log.trace(" Attribute: {} = {}", key , value);
       }
       else{
        List<Object> values = new ArrayList<>();
        for( int i = 1; i< parts.length;i++){
            values.add(parseValue(parts[i]));
        }
        node.addAttribute(key, values);
        log.trace(" Attribut: {} = {} (array)", key, values);
       }
    }

    /**
     * Parse attribue value - could be number, string or constant
     * @param valueStr String representation of value
     * @return parsed valu e( int, double, string)
     */
    private Object parseValue(String valueStr){

        if( valueStr.matches("-?\\d+")){
            try{
                return Integer.parseInt(valueStr);

            }catch(NumberFormatException e){

            }
        }

        if ( valueStr.matches("-?\\d+\\.\\d+")){
            try{
                return Double.parseDouble(valueStr);
            } catch(NumberFormatException e){

            }
        }

        return valueStr;

    }
    private List<String> parseBlock(String[] lines, int startLine, int lineNumber){

        List<String> blockLines = new ArrayList<>();
        int braceCount = 1;

        for( int j=startLine+1; j< lines.length;j++){
            String rawLine = lines[j];
            String trimLine = lines[j].trim();

            if ( trimLine.startsWith("//") || trimLine.isEmpty()) continue;

            for( char c : rawLine.toCharArray()){
                if ( c== '{') braceCount++;
                if( c=='}') braceCount--;
                
            }

            if( braceCount == 0){
                int lastBrace = rawLine.lastIndexOf('}');
                String withoutBrace = rawLine.substring(0,lastBrace).trim();
                if( !withoutBrace.isEmpty()) blockLines.add(withoutBrace);
                break;
            }else{
                blockLines.add(trimLine);
            }
        }

        if( braceCount!=0){
            throw new IllegalArgumentException("Unclosed block starting at line "+lineNumber);
        }

        return blockLines;
    }

    /**
     * Remove all block comments (slash-star style) from the RMS script
     * @param text RMS script text
     * @return Text with block comments removed
     */
    private String removeBlockComments(String text) {
        StringBuilder result = new StringBuilder();
        boolean inBlockComment = false;

        for (int i = 0; i < text.length(); i++) {
            if (!inBlockComment && i < text.length() - 1 && text.charAt(i) == '/' && text.charAt(i + 1) == '*') {
                // Start of block comment
                inBlockComment = true;
                i++; // Skip the '*'
            } else if (inBlockComment && i < text.length() - 1 && text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                // End of block comment
                inBlockComment = false;
                i++; // Skip the '/'
            } else if (!inBlockComment) {
                // Not in a comment, keep the character
                result.append(text.charAt(i));
            }
            // If in block comment, skip the character
        }

        return result.toString();
    }

}
