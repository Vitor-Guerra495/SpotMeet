package com.spotmeet.backend;

import com.spotmeet.backend.dto.CompleteEmailChangeDTO;
import com.spotmeet.backend.dto.ConfirmEmailChangeDTO;
import com.spotmeet.backend.dto.CodeDeliveryResultDTO;
import com.spotmeet.backend.dto.OrganizationRequestDTO;
import com.spotmeet.backend.dto.OrganizationResponseDTO;
import com.spotmeet.backend.dto.RegisterRequestDTO;
import com.spotmeet.backend.dto.AccessRequestResponseDTO;
import com.spotmeet.backend.model.AccessRequest.AccessRequestStatus;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.OrganizationRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.service.EmailService;
import com.spotmeet.backend.service.MembershipService;
import com.spotmeet.backend.service.OrganizationService;
import com.spotmeet.backend.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private EmailService emailService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepo;

	@Autowired
	private MembershipService membershipService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private OrganizationRepository organizationRepo;

	@Test
	void contextLoads() {
	}

	@Test
	void testRealGmailSmtpDelivery() {
		boolean delivered = emailService.sendVerificationEmail("spotmeeteste@gmail.com", "SpotMeet Teste", "987654");
		System.out.println(">>> RESULTADO DO TESTE DE ENVIO GMAIL SMTP: " + (delivered ? "SUCESSO (E-mail Entregue!)" : "FALHA") + " <<<");
		Assertions.assertTrue(delivered, "O envio do e-mail via Gmail SMTP deve ser bem-sucedido com as credenciais configuradas.");
	}

	@Test
	void testTwoStepSecureEmailChangeFlow() {
		// Create a user for the test
		String sourceEmail = "teste.troca." + System.currentTimeMillis() + "@spotmeet.com";
		String targetEmail = "novo.destino." + System.currentTimeMillis() + "@spotmeet.com";

		RegisterRequestDTO register = new RegisterRequestDTO();
		register.setName("Usuario Troca Email");
		register.setEmail(sourceEmail);
		register.setPassword("SenhaForte123");
		userService.registerUser(register);

		// Activate the account
		User user = userRepo.findByEmail(sourceEmail).orElseThrow();
		user.setEmailVerified(true);
		userRepo.save(user);

		// Step 1: request the change
		CodeDeliveryResultDTO res1 = userService.requestEmailChange(sourceEmail);
		Assertions.assertNotNull(res1.getOtpCode(), "Deve gerar código para e-mail atual");

		// Step 1 -> Step 2: confirm the current code and request activation on the new e-mail
		ConfirmEmailChangeDTO confirmDto = new ConfirmEmailChangeDTO(res1.getOtpCode(), targetEmail);
		CodeDeliveryResultDTO res2 = userService.confirmEmailChange(sourceEmail, confirmDto);
		Assertions.assertNotNull(res2.getOtpCode(), "Deve gerar código para novo e-mail");

		// Step 2: complete the change with the new e-mail code
		CompleteEmailChangeDTO completeDto = new CompleteEmailChangeDTO(res2.getOtpCode());
		User updated = userService.completeEmailChange(sourceEmail, completeDto);

		Assertions.assertEquals(targetEmail, updated.getEmail(), "O e-mail do usuário deve ter sido atualizado para o novo endereço");
		Assertions.assertTrue(userRepo.existsByEmail(targetEmail), "O novo e-mail deve constar no banco");
		Assertions.assertFalse(userRepo.existsByEmail(sourceEmail), "O e-mail antigo não deve mais pertencer ao usuário");
	}

	@Test
	void testOrganizationRemovalAndRejoinFlow() {
		long ts = System.currentTimeMillis();
		String ownerEmail = "dono.org." + ts + "@spotmeet.com";
		String memberEmail = "membro.org." + ts + "@spotmeet.com";

		// 1. Register owner and member
		RegisterRequestDTO registerOwner = new RegisterRequestDTO();
		registerOwner.setName("Dono Org");
		registerOwner.setEmail(ownerEmail);
		registerOwner.setPassword("SenhaForte123");
		userService.registerUser(registerOwner);

		RegisterRequestDTO registerMember = new RegisterRequestDTO();
		registerMember.setName("Membro Org");
		registerMember.setEmail(memberEmail);
		registerMember.setPassword("SenhaForte123");
		userService.registerUser(registerMember);

		// Activate accounts
		User owner = userRepo.findByEmail(ownerEmail).orElseThrow();
		owner.setEmailVerified(true);
		userRepo.save(owner);

		User member = userRepo.findByEmail(memberEmail).orElseThrow();
		member.setEmailVerified(true);
		userRepo.save(member);

		// 2. Owner creates the organization
		OrganizationRequestDTO createDto = new OrganizationRequestDTO();
		createDto.setName("Org Teste " + ts);
		createDto.setAccessKey("#CHV" + ts);
		createDto.setCnpj("12.345.678/0001-99");
		OrganizationResponseDTO org = organizationService.createOrganization(ownerEmail, createDto);

		// 3. Member requests access by key
		AccessRequestResponseDTO request = membershipService.requestAccess(memberEmail, org.getAccessKey());
		Assertions.assertEquals(AccessRequestStatus.PENDING, request.getStatus());

		// 4. Owner approves access
		membershipService.approveAccess(request.getId(), ownerEmail);

		// 5. Owner removes (revokes) the member
		membershipService.revokeAccess(member.getId(), org.getId(), ownerEmail);

		// 6. Member requests access again after removal (reported bug scenario)
		AccessRequestResponseDTO newRequest = membershipService.requestAccess(memberEmail, org.getAccessKey());
		Assertions.assertNotNull(newRequest, "A solicitação de re-ingresso deve ser criada com sucesso sem erro de duplicidade de chave");
		Assertions.assertEquals(AccessRequestStatus.PENDING, newRequest.getStatus());
	}

}
