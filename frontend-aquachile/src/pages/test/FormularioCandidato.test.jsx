import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import FormularioCandidato from '../FormularioCandidato';

describe('Componente FormularioCandidato', () => {
  it('debe renderizar el título y las etiquetas de los campos', () => {
    render(<FormularioCandidato />);

    // Verificar presencia de elementos
    expect(screen.getByLabelText(/nombre/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/correo/i)).toBeInTheDocument();
  });

  it('debe actualizar el valor del input al escribir', () => {
    render(<FormularioCandidato />);
    
    const inputNombre = screen.getByLabelText(/nombre/i);
    fireEvent.change(inputNombre, { target: { value: 'Juan Pérez' } });

    expect(inputNombre.value).toBe('Juan Pérez');
  });
});