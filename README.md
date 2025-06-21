# 🧭 DevTour

**DevTour** is a developer-friendly Java library that lets you **annotate and visualize the logical flow** of your codebase.  
Perfect for onboarding, documentation, or just understanding the architecture of complex projects.

---

## ☕ JDK Compatibility

- Requires **Java 17** or higher

---

## 📦 Maven Dependency

Add this to your `pom.xml`:

```xml
<dependency>
    <groupId>cl.prezdev</groupId>
    <artifactId>devtour</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 🚀 What does it do?

You’ll get output like:
```
======= 🧭 DEV TOUR: YOUR GUIDED RIDE THROUGH THE CODEBASE =======
🧱 NettyTcpServer  // Starts the TCP server
==================================================================
```

## 🛠️ How to use (Spring boot)

1. Create a config class with:
   ```java
    @Configuration
    @DevTourScan(value = "cl.prezdev")
    public class DevTourConfig {
        @PostConstruct
        public void init() {
            DevTourInspector.analyzeAndPrint();
        }
    }   
   ```
2. Add `@DevTour` to key classes or methods:
   ```java
   @DevTour(order = 1, description = "Starts the TCP server")
   public class NettyTcpServer {
       ...
   }

   @DevTour(order = 2, description = "Initializes TCP channel")
   public void initChannel() {
       ...
   }
    ```

    You’ll get console output like:
    ```
    ======= 🧭 DEV TOUR: YOUR GUIDED RIDE THROUGH THE CODEBASE =======
    🧱 NettyTcpServer  // Starts the TCP server
    🔧 NettyTcpServer.initChannel()  // Initializes TCP channel
    ==================================================================
    ```