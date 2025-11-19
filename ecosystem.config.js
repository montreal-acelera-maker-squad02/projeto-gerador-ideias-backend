module.exports = {
  apps: [
    {
      name: 'CriaitorBackend',
      script: 'java',
      exec_mode: 'fork',

      args: ['-jar', 'target/gerador-ideias-backend-0.0.1-SNAPSHOT.jar', '-Dspring.profiles.active=prod'],

      cwd: 'E:/Criaitor/projeto-gerador-ideias-backend',
      out_file: './logs/stdout.log',
      error_file: './logs/stderr.log',
    },
  {
        name: 'CriaitorFrontend',
        script: 'npm',
        exec_mode: 'fork',
        args: ['start'],
        cwd: 'E:/Criaitor/projeto-gerador-ideias-frontend',
        out_file: './logs/stdout_frontend.log',
        error_file: './logs/stderr_frontend.log',
      },
    ],
  };