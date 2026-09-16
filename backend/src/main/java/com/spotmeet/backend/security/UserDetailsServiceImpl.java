package com.spotmeet.backend.security;

import com.spotmeet.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * UserDetailsService implementation.
 * Spring Security uses it to load the user by "username" (e-mail)
 * during authentication and JWT validation.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Loads the user by e-mail. The User entity implements UserDetails,
     * so it is returned directly.
     *
     * @param email User e-mail (used as username in SpotMeet).
     * @throws UsernameNotFoundException if the e-mail does not exist.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado com o e-mail: " + email
                ));
    }
}
