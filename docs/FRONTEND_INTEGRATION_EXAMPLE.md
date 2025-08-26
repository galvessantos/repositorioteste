# Exemplo de Integração Frontend - Redefinição de Senha

## Visão Geral

Este documento fornece exemplos práticos de como integrar o frontend com a API de redefinição de senha.

**Importante:** A validação é feita **APENAS pelo login (username)**, não por email. O email será usado apenas para envio do link de redefinição.

## Estrutura de Arquivos Recomendada

```
src/
├── services/
│   └── passwordResetService.ts
├── components/
│   ├── ForgotPasswordModal.tsx
│   └── PasswordResetForm.tsx
├── types/
│   └── passwordReset.types.ts
└── utils/
    └── validation.ts
```

## 1. Tipos TypeScript

### `src/types/passwordReset.types.ts`
```typescript
export interface PasswordResetGenerateRequest {
  login: string; // APENAS username, não email
}

export interface PasswordResetGenerateResponse {
  message: string;
  resetLink: string | null;
}

export interface PasswordResetValidateResponse {
  valid: boolean;
  message: string;
}

export interface PasswordResetRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}

export interface PasswordResetResponse {
  message: string;
  success: boolean;
}

export interface PasswordValidationResult {
  isValid: boolean;
  errors: string[];
}
```

## 2. Serviço de API

### `src/services/passwordResetService.ts`
```typescript
import {
  PasswordResetGenerateRequest,
  PasswordResetGenerateResponse,
  PasswordResetValidateResponse,
  PasswordResetRequest,
  PasswordResetResponse
} from '../types/passwordReset.types';

class PasswordResetService {
  private baseUrl = '/api/auth/password-reset';

  /**
   * Solicita redefinição de senha
   */
  async requestReset(request: PasswordResetGenerateRequest): Promise<PasswordResetGenerateResponse> {
    try {
      const response = await fetch(`${this.baseUrl}/generate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        if (response.status === 404) {
          return {
            message: 'Login informado inválido',
            resetLink: null
          };
        }
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error('Erro ao solicitar redefinição:', error);
      throw new Error('Erro ao processar solicitação de redefinição');
    }
  }

  /**
   * Valida token de redefinição
   */
  async validateToken(token: string): Promise<PasswordResetValidateResponse> {
    try {
      const response = await fetch(`${this.baseUrl}/validate?token=${encodeURIComponent(token)}`);
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error('Erro ao validar token:', error);
      throw new Error('Erro ao validar link de redefinição');
    }
  }

  /**
   * Redefine a senha
   */
  async resetPassword(request: PasswordResetRequest): Promise<PasswordResetResponse> {
    try {
      const response = await fetch(`${this.baseUrl}/reset`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        if (response.status === 400) {
          const errorData = await response.json();
          return errorData;
        }
        if (response.status === 404) {
          return {
            message: 'Token inválido ou expirado',
            success: false
          };
        }
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      return response.json();
    } catch (error) {
      console.error('Erro ao redefinir senha:', error);
      throw new Error('Erro interno ao redefinir senha');
    }
  }
}

export const passwordResetService = new PasswordResetService();
```

## 3. Utilitários de Validação

### `src/utils/validation.ts`
```typescript
export interface PasswordValidationResult {
  isValid: boolean;
  errors: string[];
  strength: 'weak' | 'medium' | 'strong';
}

export const validatePassword = (password: string): PasswordValidationResult => {
  const errors: string[] = [];
  
  // Validação de tamanho
  if (password.length < 4) {
    errors.push('A senha deve ter pelo menos 4 caracteres');
  } else if (password.length > 8) {
    errors.push('A senha deve ter no máximo 8 caracteres');
  }

  // Validação de letras minúsculas
  if (!password.match(/[a-z]/)) {
    errors.push('A senha deve conter pelo menos uma letra minúscula');
  }

  // Validação de letras maiúsculas
  if (!password.match(/[A-Z]/)) {
    errors.push('A senha deve conter pelo menos uma letra maiúscula');
  }

  // Validação de números
  if (!password.match(/\d/)) {
    errors.push('A senha deve conter pelo menos um número');
  }

  // Validação de caracteres especiais
  if (!password.match(/[_@#]/)) {
    errors.push('A senha deve conter pelo menos um dos caracteres especiais: _ @ #');
  }

  // Cálculo de força da senha
  let strength: 'weak' | 'medium' | 'strong' = 'weak';
  if (errors.length === 0) {
    if (password.length >= 6 && password.match(/[_@#]/) && password.match(/\d/)) {
      strength = 'strong';
    } else {
      strength = 'medium';
    }
  }

  return {
    isValid: errors.length === 0,
    errors,
    strength
  };
};

export const getPasswordStrengthColor = (strength: string): string => {
  switch (strength) {
    case 'strong': return '#22c55e';
    case 'medium': return '#f59e0b';
    case 'weak': return '#ef4444';
    default: return '#6b7280';
  }
};

export const getPasswordStrengthText = (strength: string): string => {
  switch (strength) {
    case 'strong': return 'Forte';
    case 'medium': return 'Média';
    case 'weak': return 'Fraca';
    default: return 'Inválida';
  }
};
```

## 4. Componente Modal "Esqueci minha senha"

### `src/components/ForgotPasswordModal.tsx`
```typescript
import React, { useState } from 'react';
import { passwordResetService } from '../services/passwordResetService';

interface ForgotPasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ForgotPasswordModal: React.FC<ForgotPasswordModalProps> = ({ isOpen, onClose }) => {
  const [login, setLogin] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!login.trim()) {
      setMessage({ type: 'error', text: 'Por favor, informe o login' });
      return;
    }

    setIsLoading(true);
    setMessage(null);

    try {
      const response = await passwordResetService.requestReset({ login: login.trim() });
      
      if (response.resetLink) {
        setMessage({ 
          type: 'success', 
          text: 'Link de redefinição enviado para seu e-mail. Verifique sua caixa de entrada.' 
        });
        // Limpar campo após sucesso
        setLogin('');
      } else {
        setMessage({ type: 'error', text: response.message });
      }
    } catch (error) {
      setMessage({ 
        type: 'error', 
        text: 'Erro ao processar solicitação. Tente novamente.' 
      });
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md mx-4">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-semibold text-gray-900">
            Esqueci minha senha
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="login" className="block text-sm font-medium text-gray-700 mb-1">
              Login (username)
            </label>
            <input
              type="text"
              id="login"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Digite seu login"
              disabled={isLoading}
            />
          </div>

          {message && (
            <div className={`p-3 rounded-md ${
              message.type === 'success' 
                ? 'bg-green-50 text-green-800 border border-green-200' 
                : 'bg-red-50 text-red-800 border border-red-200'
            }`}>
              {message.text}
            </div>
          )}

          <div className="flex space-x-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 disabled:opacity-50"
              disabled={isLoading}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={isLoading || !login.trim()}
              className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? 'Enviando...' : 'Enviar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
```

## 5. Componente de Redefinição de Senha

### `src/components/PasswordResetForm.tsx`
```typescript
import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { passwordResetService } from '../services/passwordResetService';
import { validatePassword, getPasswordStrengthColor, getPasswordStrengthText } from '../utils/validation';

export const PasswordResetForm: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [formData, setFormData] = useState({
    newPassword: '',
    confirmPassword: ''
  });
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [passwordValidation, setPasswordValidation] = useState(validatePassword(''));

  // Validar token ao carregar o componente
  useEffect(() => {
    if (!token) {
      setMessage({ type: 'error', text: 'Token de redefinição não encontrado' });
      return;
    }

    const validateToken = async () => {
      try {
        const response = await passwordResetService.validateToken(token);
        if (!response.valid) {
          setMessage({ type: 'error', text: 'Link de redefinição inválido ou expirado' });
        }
      } catch (error) {
        setMessage({ type: 'error', text: 'Erro ao validar link de redefinição' });
      }
    };

    validateToken();
  }, [token]);

  // Validar senha em tempo real
  useEffect(() => {
    setPasswordValidation(validatePassword(formData.newPassword));
  }, [formData.newPassword]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!token) {
      setMessage({ type: 'error', text: 'Token de redefinição não encontrado' });
      return;
    }

    if (!passwordValidation.isValid) {
      setMessage({ type: 'error', text: 'Por favor, corrija os erros na senha' });
      return;
    }

    if (formData.newPassword !== formData.confirmPassword) {
      setMessage({ type: 'error', text: 'As senhas não coincidem' });
      return;
    }

    setIsLoading(true);
    setMessage(null);

    try {
      const response = await passwordResetService.resetPassword({
        token,
        newPassword: formData.newPassword,
        confirmPassword: formData.confirmPassword
      });

      if (response.success) {
        setMessage({ 
          type: 'success', 
          text: 'Senha redefinida com sucesso! Redirecionando para o login...' 
        });
        
        // Redirecionar para login após 2 segundos
        setTimeout(() => {
          navigate('/login');
        }, 2000);
      } else {
        setMessage({ type: 'error', text: response.message });
      }
    } catch (error) {
      setMessage({ 
        type: 'error', 
        text: 'Erro ao redefinir senha. Tente novamente.' 
      });
    } finally {
      setIsLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="max-w-md w-full space-y-8">
          <div className="text-center">
            <h2 className="text-3xl font-bold text-gray-900">Erro</h2>
            <p className="mt-2 text-gray-600">
              Token de redefinição não encontrado
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center">
          <h2 className="text-3xl font-bold text-gray-900">Redefinir Senha</h2>
          <p className="mt-2 text-gray-600">
            Digite sua nova senha
          </p>
        </div>

        <form onSubmit={handleSubmit} className="mt-8 space-y-6">
          <div className="space-y-4">
            <div>
              <label htmlFor="newPassword" className="block text-sm font-medium text-gray-700">
                Nova Senha
              </label>
              <input
                type="password"
                id="newPassword"
                value={formData.newPassword}
                onChange={(e) => setFormData({ ...formData, newPassword: e.target.value })}
                className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Digite a nova senha"
                disabled={isLoading}
              />
              
              {/* Indicador de força da senha */}
              {formData.newPassword && (
                <div className="mt-2">
                  <div className="flex items-center space-x-2">
                    <div 
                      className="w-2 h-2 rounded-full"
                      style={{ backgroundColor: getPasswordStrengthColor(passwordValidation.strength) }}
                    />
                    <span className="text-sm text-gray-600">
                      {getPasswordStrengthText(passwordValidation.strength)}
                    </span>
                  </div>
                </div>
              )}

              {/* Lista de erros de validação */}
              {passwordValidation.errors.length > 0 && (
                <ul className="mt-2 text-sm text-red-600 space-y-1">
                  {passwordValidation.errors.map((error, index) => (
                    <li key={index}>• {error}</li>
                  ))}
                </ul>
              )}
            </div>

            <div>
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700">
                Confirmar Senha
              </label>
              <input
                type="password"
                id="confirmPassword"
                value={formData.confirmPassword}
                onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Confirme a nova senha"
                disabled={isLoading}
              />
            </div>
          </div>

          {message && (
            <div className={`p-3 rounded-md ${
              message.type === 'success' 
                ? 'bg-green-50 text-green-800 border border-green-200' 
                : 'bg-red-50 text-red-800 border border-red-200'
            }`}>
              {message.text}
            </div>
          )}

          <button
            type="submit"
            disabled={isLoading || !passwordValidation.isValid || formData.newPassword !== formData.confirmPassword}
            className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isLoading ? 'Redefinindo...' : 'Redefinir Senha'}
          </button>
        </form>

        {/* Critérios de senha */}
        <div className="bg-gray-50 p-4 rounded-md">
          <h3 className="text-sm font-medium text-gray-900 mb-2">Critérios da senha:</h3>
          <ul className="text-sm text-gray-600 space-y-1">
            <li>• Tamanho: mínimo 4, máximo 8 caracteres</li>
            <li>• Composição: pelo menos 1 letra maiúscula e 1 minúscula</li>
            <li>• Caracteres especiais: pelo menos 1 dos seguintes: _ @ #</li>
            <li>• Números: pelo menos 1 dígito</li>
          </ul>
        </div>
      </div>
    </div>
  );
};
```

## 6. Uso dos Componentes

### Exemplo de integração no App principal
```typescript
import React, { useState } from 'react';
import { ForgotPasswordModal } from './components/ForgotPasswordModal';
import { PasswordResetForm } from './components/PasswordResetForm';

function App() {
  const [showForgotPassword, setShowForgotPassword] = useState(false);

  return (
    <div>
      {/* Botão para abrir modal */}
      <button onClick={() => setShowForgotPassword(true)}>
        Esqueci minha senha
      </button>

      {/* Modal de esqueci minha senha */}
      <ForgotPasswordModal
        isOpen={showForgotPassword}
        onClose={() => setShowForgotPassword(false)}
      />

      {/* Rota para redefinição de senha */}
      <Route path="/reset-password" element={<PasswordResetForm />} />
    </div>
  );
}
```

## 7. Configuração de Rotas

### Exemplo com React Router
```typescript
import { BrowserRouter, Routes, Route } from 'react-router-dom';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/reset-password" element={<PasswordResetForm />} />
        {/* outras rotas */}
      </Routes>
    </BrowserRouter>
  );
}
```

## 8. Tratamento de Erros Globais

### Interceptor para requisições HTTP
```typescript
// Adicione um interceptor global para tratar erros HTTP
const handleHttpError = (error: any) => {
  if (error.status === 401) {
    // Redirecionar para login
    navigate('/login');
  } else if (error.status === 403) {
    // Mostrar erro de permissão
    showError('Você não tem permissão para realizar esta ação');
  } else if (error.status >= 500) {
    // Erro do servidor
    showError('Erro interno do servidor. Tente novamente mais tarde.');
  }
};
```

## 9. Testes

### Exemplo de teste com Jest e Testing Library
```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ForgotPasswordModal } from './ForgotPasswordModal';

describe('ForgotPasswordModal', () => {
  test('should submit form with valid login', async () => {
    const mockOnClose = jest.fn();
    
    render(<ForgotPasswordModal isOpen={true} onClose={mockOnClose} />);
    
    const loginInput = screen.getByPlaceholderText('Digite seu login');
    const submitButton = screen.getByText('Enviar');
    
    fireEvent.change(loginInput, { target: { value: 'usuario123' } });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText(/Link de redefinição enviado/)).toBeInTheDocument();
    });
  });
});
```

## 10. Considerações de Segurança

- Sempre valide dados no frontend antes de enviar
- Use HTTPS em produção
- Implemente rate limiting para evitar spam
- Valide tokens no frontend antes de mostrar formulários
- Sanitize inputs para prevenir XSS
- Implemente logout automático em caso de erro de autenticação

## 11. Performance

- Implemente debounce na validação de senha
- Use React.memo para componentes que não mudam frequentemente
- Implemente lazy loading para rotas de redefinição
- Cache tokens válidos temporariamente
- Implemente retry automático para falhas de rede

Esta implementação fornece uma base sólida para a integração com a API de redefinição de senha, com tratamento de erros, validações em tempo real e uma experiência de usuário fluida.

### 3. Redefinição de Senha com Login Automático
```typescript
const handlePasswordReset = async (token: string, newPassword: string, confirmPassword: string) => {
  try {
    const response = await passwordResetService.resetPassword({
      token,
      newPassword,
      confirmPassword
    });

    if (response.success) {
      // 🎯 LOGIN AUTOMÁTICO - Usuário já está autenticado!
      const { accessToken, refreshToken, expiresIn } = response;
      
      // Salvar tokens no localStorage ou estado da aplicação
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('tokenExpiresIn', expiresIn.toString());
      
      // Configurar header de autorização para todas as requisições
      axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
      
      // Mostrar sucesso e redirecionar para tela principal
      showSuccess('Senha redefinida com sucesso! Você já está logado no sistema.');
      
      // Redirecionar para tela principal (já autenticado)
      navigate('/dashboard');
      
    } else {
      // Mostrar erro específico
      showError(response.message);
    }
  } catch (error) {
    showError('Erro ao redefinir senha');
  }
};
```

## 🎯 **Login Automático - Como Funciona**

### **1. Fluxo Tradicional (ANTES):**
```
Usuário redefine senha → Sucesso → Redireciona para login → Usuário faz login novamente
```

### **2. Fluxo com Login Automático (AGORA):**
```
Usuário redefine senha → Sucesso → Recebe tokens JWT → Acesso direto ao sistema
```

### **3. Benefícios:**
- ✅ **Experiência fluida** - Usuário não precisa fazer login novamente
- ✅ **Segurança mantida** - Tokens JWT com expiração
- ✅ **Acesso imediato** - Pode usar o sistema logo após redefinição
- ✅ **Refresh automático** - Token de refresh para renovar acesso

### **4. Implementação no Frontend:**

#### **Configurar Interceptor Axios:**
```typescript
// Configurar token automaticamente
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Renovar token automaticamente
axios.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken) {
        try {
          const newTokens = await refreshAuthToken(refreshToken);
          localStorage.setItem('accessToken', newTokens.accessToken);
          error.config.headers.Authorization = `Bearer ${newTokens.accessToken}`;
          return axios(error.config);
        } catch (refreshError) {
          // Redirecionar para login
          navigate('/login');
        }
      }
    }
    return Promise.reject(error);
  }
);
```

#### **Serviço de Autenticação Atualizado:**
```typescript
class AuthService {
  private baseUrl = '/api/auth';
  
  // Verificar se usuário está autenticado
  isAuthenticated(): boolean {
    const token = localStorage.getItem('accessToken');
    const expiresIn = localStorage.getItem('tokenExpiresIn');
    
    if (!token || !expiresIn) return false;
    
    const expirationTime = parseInt(expiresIn) * 1000; // Converter para milissegundos
    const currentTime = Date.now();
    
    return currentTime < expirationTime;
  }
  
  // Fazer logout
  logout(): void {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('tokenExpiresIn');
    delete axios.defaults.headers.common['Authorization'];
    navigate('/login');
  }
  
  // Renovar token
  async refreshToken(): Promise<void> {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) throw new Error('No refresh token');
    
    const response = await axios.post(`${this.baseUrl}/refresh-token`, {
      refreshToken
    });
    
    const { accessToken, expiresIn } = response.data;
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('tokenExpiresIn', expiresIn.toString());
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
  }
}
```