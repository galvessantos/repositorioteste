package com.montreal.msiav_bh.utils;

import com.montreal.msiav_bh.entity.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressParser {

    public Address parseAddress(String enderecoCompleto) {
        if (enderecoCompleto == null || enderecoCompleto.trim().isEmpty()) {
            return createDefaultAddress();
        }

        try {
            // Exemplo de parse para: "R. A, 4-24 - Marambaia - Belém - PA. Cep: 66620720"
            String[] parts = enderecoCompleto.split(" - ");

            Address.AddressBuilder builder = Address.builder();

            if (parts.length >= 4) {
                // Parse da primeira parte: "R. A, 4-24"
                String ruaNumero = parts[0].trim();
                String[] ruaNumeroSplit = ruaNumero.split(", ");

                if (ruaNumeroSplit.length >= 2) {
                    builder.street(ruaNumeroSplit[0].trim());
                    builder.number(ruaNumeroSplit[1].trim().replaceAll("[^\\d]", "")); // Remove não-dígitos
                } else {
                    builder.street(ruaNumero);
                    builder.number("1"); // Número padrão válido
                }

                // Bairro
                builder.neighborhood(parts[1].trim());

                // Cidade
                builder.city(parts[2].trim());

                // Estado e CEP na última parte
                String estadoCep = parts[3].trim();
                String[] estadoCepSplit = estadoCep.split("\\. Cep: ");

                if (estadoCepSplit.length >= 2) {
                    builder.state(estadoCepSplit[0].trim());
                    String cep = estadoCepSplit[1].trim();
                    // Formatar CEP para o padrão 99999-999
                    if (cep.length() == 8) {
                        cep = cep.substring(0, 5) + "-" + cep.substring(5);
                    }
                    builder.postalCode(cep);
                } else {
                    builder.state(estadoCep);
                    builder.postalCode("00000-000");
                }

            } else {
                // Fallback para endereços com formato diferente
                builder.street(enderecoCompleto)
                        .number("1")
                        .neighborhood("Centro")
                        .city("Não Informado")
                        .state("BR")
                        .postalCode("00000-000");
            }

            return builder.build();

        } catch (Exception e) {
            // Em caso de erro no parse, retorna endereço padrão
            return createDefaultAddressWithNote(enderecoCompleto);
        }
    }

    private Address createDefaultAddress() {
        return Address.builder()
                .street("Não Informado")
                .number("1")
                .neighborhood("Centro")
                .city("Não Informado")
                .state("BR")
                .postalCode("00000-000")
                .build();
    }

    private Address createDefaultAddressWithNote(String originalAddress) {
        return Address.builder()
                .street("Não Informado")
                .number("1")
                .neighborhood("Centro")
                .city("Não Informado")
                .state("BR")
                .postalCode("00000-000")
                .note("Endereço original: " + originalAddress)
                .build();
    }
}
