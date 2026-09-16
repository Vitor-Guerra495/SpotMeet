package com.spotmeet.backend;

import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    /**
     * SysAdmin bootstrap, executed once at startup.
     * Creates the SysAdmin user if it does not exist yet.
     *
     * Security: change the default password in production, e.g. via
     * System.getenv("SPOTMEET_SYSADMIN_PASSWORD").
     */
    @Bean
    public ApplicationRunner sysAdminBootstrap(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            final String SYSADMIN_EMAIL = "sysadmin@spotmeet.com";

            if (repo.findByEmail(SYSADMIN_EMAIL).isEmpty()) {
                User admin = new User();
                admin.setName("Administrador do Sistema");
                admin.setEmail(SYSADMIN_EMAIL);
                admin.setPassword(encoder.encode("Admin@123")); // BCrypt hash
                admin.setRole("SYSADMIN");
                admin.setEmailVerified(true);

                repo.save(admin);

                System.out.println("╔════════════════════════════════════════════════╗");
                System.out.println("║         [SpotMeet] SysAdmin Provisionado       ║");
                System.out.println("║  Email: sysadmin@spotmeet.com                  ║");
                System.out.println("║  Senha: Admin@123                              ║");
                System.out.println("║  AVISO: ALTERE A SENHA EM PRODUCAO!           ║");
                System.out.println("╚════════════════════════════════════════════════╝");
            }
        };
    }
}
