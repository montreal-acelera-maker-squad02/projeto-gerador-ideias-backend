module.exports = {
  apps: [
    {
      name: 'CriaitorBackend',
      script: 'java',
      exec_mode: 'fork',
      args: [
        '-jar',
        'target/gerador-ideias-backend-0.0.1-SNAPSHOT.jar',
        '-Dspring.profiles.active=prod'
      ],
      cwd: 'E:/actions-runner/_work/projeto-gerador-ideias-backend/projeto-gerador-ideias-backend',
      out_file: './logs/stdout.log',
      error_file: './logs/stderr.log',
    },
    {
      name: 'CriaitorFrontend',
      script: 'cmd.exe',
      args: [
        '/c',
        'node_modules\\.bin\\http-server.cmd',
        'dist',
        '-p',
        '5174',
        '-a',
        '0.0.0.0',
        '--silent'
      ],
      cwd: 'E:/actions-runner-frontend/_work/projeto-gerador-ideias-frontend/projeto-gerador-ideias-frontend',
      out_file: './logs/stdout_frontend.log',
      error_file: './logs/stderr_frontend.log',
    }
  ]
};
