package com.example.docmind.service;

import com.example.docmind.entity.User;
import com.example.docmind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Used only by the login AuthenticationManager. Accepts either the username or the email address.
@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String value = login == null ? "" : login.trim();
        User user = userRepository.findByUsernameIgnoreCase(value)
                .or(() -> userRepository.findByEmailIgnoreCase(value))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid Username or Password"));
        return new CustomUserDetail(user);
    }
}
