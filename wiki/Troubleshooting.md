# Troubleshooting

This guide helps you solve common issues with Web GameList Oper.

## Common Issues

### Issue: Application won't start

**Symptoms:**
- Container exits immediately
- Error messages in logs
- Cannot access http://localhost:8081

**Solutions:**
1. **Check port availability**
   ```bash
   netstat -tlnp | grep 8081
   ```
   Use a different port if 8081 is in use.

2. **Check directory permissions**
   ```bash
   chmod -R 755 ./data ./output ./logs ./backup
   ```

3. **Check Docker logs**
   ```bash
   docker logs webgamelistoper
   ```

### Issue: Import templates not loading

**Symptoms:**
- "No templates available" message
- Empty template dropdown

**Solutions:**
1. **Verify rules directory exists**
   ```bash
   ls -la ./data/rules/import/
   ```

2. **Check file permissions**
   ```bash
   chmod 644 ./data/rules/import/*.json
   ```

3. **Verify JSON format**
   Ensure template files are valid JSON.

### Issue: Export rules not found

**Symptoms:**
- "No export rules loaded" warning
- Empty export format dropdown

**Solutions:**
1. **Check export rules directory**
   ```bash
   ls -la ./data/rules/export/
   ```

2. **Verify file content**
   Ensure JSON files have required fields:
   - `frontend`
   - `name`
   - `exportPath`

### Issue: Template import garbled characters

**Symptoms:**
- Chinese/Japanese characters appear as `???`
- Imported game names are corrupted

**Solutions:**
1. **Ensure UTF-8 encoding**
   Make sure your XML files are saved in UTF-8 encoding.

2. **Check file encoding**
   ```bash
   file -I game_list.xml
   ```
   Should show `charset=utf-8`

### Issue: JAXB unmarshal error

**Symptoms:**
- `JAXB解组失败: null` error in logs
- Import fails silently

**Solutions:**
1. **Check XML file is not empty**
   ```bash
   cat game_list.xml
   ```

2. **Verify XML structure**
   Ensure the XML has correct root element and structure.

3. **Check file permissions**
   Ensure the application can read the file.

### Issue: Database connection error

**Symptoms:**
- "Cannot connect to database" error
- Application fails to start

**Solutions:**
1. **Check database directory**
   ```bash
   mkdir -p ./data/database
   chmod 755 ./data/database
   ```

2. **Check SQLite dependency**
   Ensure `sqlite-jdbc` dependency is included.

### Issue: Media files not showing

**Symptoms:**
- "No media files" message
- Images/videos not displaying

**Solutions:**
1. **Check media directory structure**
   ```
   data/roms/
   └── [platform]/
       └── [game]/
           ├── boxfront.jpg
           └── video.mp4
   ```

2. **Verify file permissions**
   ```bash
   chmod -R 644 ./data/roms/
   ```

3. **Check file extensions**
   Supported formats: jpg, png, mp4, webm

### Issue: Language not switching

**Symptoms:**
- Language dropdown doesn't work
- `getI18nText is not defined` error

**Solutions:**
1. **Clear browser cache**
   ```
   Ctrl + Shift + R (hard refresh)
   ```

2. **Check translation config**
   Ensure `translation-config.json` exists and is valid.

3. **Check JavaScript console**
   Look for errors in browser developer tools (F12).

### Issue: High memory usage

**Symptoms:**
- Application runs slowly
- OutOfMemoryError
- Container killed by OOM killer

**Solutions:**
1. **Increase memory allocation**
   ```bash
   java -Xmx4g -Xms1g -jar webGamelistOper-1.0.4-beta.jar
   ```

2. **Use G1GC collector**
   ```bash
   java -XX:+UseG1GC -Xmx4g -jar webGamelistOper-1.0.4-beta.jar
   ```

3. **Limit concurrent imports**
   Avoid importing too many files at once.

## Log Analysis

### Locating Logs
- **Docker**: `/app/logs/`
- **Local**: `./logs/`

### Common Log Patterns

**INFO Messages:**
```
INFO  c.g.s.impl.ExportRuleServiceImpl - Loading export rules from: /data/rules/export
INFO  c.g.xml.GameListParser - Creating unmarshaller...
```

**WARN Messages:**
```
WARN  c.g.s.impl.ExportRuleServiceImpl - No export rules loaded from: /data/rules/export
```

**ERROR Messages:**
```
ERROR c.g.xml.GameListParser - JAXB解组失败: null
```

## Getting Help

If you encounter an issue not covered here:

1. **Check GitHub Issues**: [Frontend-Killer Issues](https://github.com/fansmall2008/Frontend-Killer/issues)

2. **Provide Information**:
   - Docker/Java version
   - Log files
   - Steps to reproduce
   - Screenshots if applicable

3. **Contact Developer**:
   - Create an issue on GitHub
   - Include relevant details

## FAQ

### Q: Can I run multiple instances?
A: Yes, but each instance should have its own data directory.

### Q: How do I upgrade?
A: Pull the latest image and restart the container. Data is persisted in volumes.

### Q: Does it support Windows?
A: Yes, you can run the JAR file directly on Windows.

### Q: How do I backup my data?
A: Copy the `./data` directory to a safe location.

### Q: What browsers are supported?
A: Chrome, Firefox, Safari, and Edge (latest versions).