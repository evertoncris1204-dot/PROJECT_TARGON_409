package net.sf.l2j.gameserver.handler;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.sf.l2j.commons.logging.CLogger;

public abstract class AbstractHandler<K, H>
{
	private static final CLogger LOGGER = new CLogger(AbstractHandler.class.getName());
	
	protected final Map<K, H> _entries = new HashMap<>();
	
	protected abstract void registerHandler(H handler);
	
	protected AbstractHandler(Class<H> handlerInterface, String className)
	{
		final String packagePath = "net/sf/l2j/gameserver/handler/" + className;
		
		try
		{
			final Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(packagePath);
			while (resources.hasMoreElements())
			{
				final URL resource = resources.nextElement();
				
				// Handle debug process.
				if (resource.getProtocol().equals("file"))
				{
					final File directory = new File(resource.getFile());
					if (!directory.exists())
						continue;
					
					final String packageName = packagePath.replace("/", ".");
					
					for (String file : directory.list())
					{
						if (!file.endsWith(".class"))
							continue;
						
						final Class<?> clazz = Class.forName(packageName + "." + file.substring(0, file.length() - 6));
						if (!handlerInterface.isAssignableFrom(clazz) || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()))
							continue;
						
						registerHandler(handlerInterface.cast(clazz.getDeclaredConstructor().newInstance()));
					}
				}
				// Handle regular JAR process.
				else if (resource.getProtocol().equals("jar"))
				{
					final JarURLConnection conn = (JarURLConnection) resource.openConnection();
					conn.setUseCaches(false); // Prevent caching issues
					final JarFile jarFile = conn.getJarFile();
					try
					{
						// Collect entries first to avoid issues with closed JAR
						final Enumeration<JarEntry> entries = jarFile.entries();
						final java.util.List<String> classNames = new java.util.ArrayList<>();
						
						while (entries.hasMoreElements())
						{
							final JarEntry entry = entries.nextElement();
							final String entryName = entry.getName();
							
							if (!entryName.startsWith(packagePath) || !entryName.endsWith(".class"))
								continue;
							
							classNames.add(entryName.replace('/', '.').replace(".class", ""));
						}
						
						// Now instantiate classes after JAR iteration is complete
						for (String clsName : classNames)
						{
							try
							{
								final Class<?> clazz = Class.forName(clsName);
								if (!handlerInterface.isAssignableFrom(clazz) || clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers()))
									continue;
								
								registerHandler(handlerInterface.cast(clazz.getDeclaredConstructor().newInstance()));
							}
							catch (Exception e)
							{
								LOGGER.warn("Failed to instantiate class {}: {}", e, clsName, e.getMessage());
							}
						}
					}
					finally
					{
						// Close the JAR file manually
						try
						{
							jarFile.close();
						}
						catch (Exception e)
						{
							// Ignore close errors
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to load classes from package {}", e, packagePath);
		}
	}
	
	public int size()
	{
		return _entries.size();
	}
	
	public H getHandler(Object key)
	{
		return _entries.getOrDefault(key, null);
	}
}