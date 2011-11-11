/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.tools.test;

import jetbrains.buildServer.tools.BaseTestCase;
import jetbrains.buildServer.tools.Continuation;
import jetbrains.buildServer.tools.ErrorReporting;
import jetbrains.buildServer.tools.ScanFile;
import jetbrains.buildServer.tools.rules.PathSettings;
import jetbrains.buildServer.tools.rules.RulesParser;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jetbrains.annotations.NotNull;
import org.jmock.Mockery;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 *         Date: 11.11.11 15:29
 */
public class RulesBaseTestCase extends BaseTestCase {
  protected Mockery m;
  protected Continuation c;
  protected File myHome;
  protected ErrorReporting rep;


  @BeforeMethod
  @Override
  public void setUp() throws IOException {
    super.setUp();
    myHome = createTempDir();
    m = new Mockery();
    c = m.mock(Continuation.class);
    rep = m.mock(ErrorReporting.class);
  }

  @AfterMethod
  @Override
  public void tearDown() {
    super.tearDown();
    m.assertIsSatisfied();
  }

  protected PathSettings parseConfig(@NotNull final String configText) throws IOException {
    return RulesParser.parseConfig(myHome, new StringReader(configText));
  }

  @NotNull
  public ScanFile mockFile(@NotNull final String relPath) {
    final String path = myHome.getPath() + File.separatorChar + relPath;
    return new ScanFile() {
      @NotNull
      public InputStream openStream() throws IOException {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public String getName() {
        return path;
      }

      public boolean isFile() {
        return false;
      }
    };
  }

  @NotNull
  public ScanFile mockFile(@NotNull final String relPath, @NotNull final byte[] content) {
    final String path = new File(myHome, relPath).getPath();
    return new ScanFile() {
      @NotNull
      public InputStream openStream() throws IOException {
        return new ByteArrayInputStream(content);
      }

      @NotNull
      public String getName() {
        return path;
      }

      public boolean isFile() {
        return false;
      }
    };
  }


  protected class Expectations extends org.jmock.Expectations {
    protected BaseMatcher<ScanFile> file(@NotNull final String path) {
      final String name = mockFile(path).getName();
      return new BaseMatcher<ScanFile>() {
        public boolean matches(Object item) {
          ScanFile sf = (ScanFile) item;
          return sf.getName().equals(name);
        }

        public void describeTo(Description description) {
          description.appendText("ScanFile{" + name + "}");
        }
      };
    }
  }

  public void expectFile(@NotNull final String name) {
    m.checking(new Expectations(){{
      oneOf(c).postTask(with(file(name)));
    }});
  }

  public void expectCheckError(@NotNull final String name) {
    m.checking(new Expectations(){{
      oneOf(rep).postCheckError(with(file(name)), with(any(String.class)));
    }});
  }

  public void expectGenericError(@NotNull final String name) {
    m.checking(new Expectations(){{
      oneOf(rep).postError(with(file(name)), with(any(String.class)));
    }});
  }


  @NotNull
  protected byte[] zipStream(@NotNull final String... files) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    zipStream(os, files);

    os.close();
    return os.toByteArray();
  }

  private void zipStream(@NotNull final OutputStream os, @NotNull final String[] files) throws IOException {
    final ZipOutputStream zos = new ZipOutputStream(os);

    for (String file : files) {
      if (!file.contains(".")) {
        zos.putNextEntry(new ZipEntry(file));
      } else {
        int sp = file.indexOf('!');
        if (sp < 0) {
          zos.putNextEntry(new ZipEntry(file));
          zos.write(file.getBytes());
        } else {
          final String path = file.substring(0, sp);
          zos.putNextEntry(new ZipEntry(path));

          final String[] rest = file.substring(sp).split(",");
          zipStream(zos, rest);
        }
      }
    }
    zos.finish();
  }


}
