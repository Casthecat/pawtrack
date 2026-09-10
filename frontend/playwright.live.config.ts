import { defineConfig } from '@playwright/test'

// A fresh demo process owns disposable H2 data; never reuse an existing backend.
export default defineConfig({
  testDir: './tests/live',
  workers: 1,
  retries: 0,
  forbidOnly: true,
  reporter: 'list',
  outputDir: 'test-results/live',
  use: {
    baseURL: 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 1100 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: `${process.platform === 'win32' ? 'mvn.cmd' : 'mvn'} spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--server.port=19091"`,
      cwd: '../backend',
      stdout: 'pipe',
      url: 'http://127.0.0.1:19091/actuator/health',
      timeout: 120000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5173 --strictPort',
      env: { PAWTRACK_API_TARGET: 'http://127.0.0.1:19091' },
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: false,
    },
  ],
})
