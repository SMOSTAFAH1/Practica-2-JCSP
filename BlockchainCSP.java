package cc.blockchain;

import org.jcsp.lang.*;

import es.upm.aedlib.map.HashTableMap;
import es.upm.aedlib.map.Map;
import java.util.ArrayList;

/**
 * La clase BlockchainCSP implementa una blockchain utilizando procesos
 * concurrentes con JCSP.
 */
public class BlockchainCSP implements Blockchain, CSProcess {

	private Any2OneChannel chCrear;
	private Any2OneChannel chDisponible;
	private Any2OneChannel chTransferir;
	private Any2OneChannel chAlertar;

	private Map<String, Integer> cuentas;
	private Map<String, String> identidades;
	private ArrayList<PetTransferir> peticionesTransferir;
	private ArrayList<PetAlertar> peticionesAlertar;

	/**
	 * Clase interna para manejar las peticiones de creación de cuentas.
	 */
	public class PetCrear {
		String idPrivado;
		String idPublico;
		int saldo;
		One2OneChannel resp;

		/**
		 * Constructor para la petición de creación de cuentas.
		 *
		 * @param idPrivado ID privado de la cuenta
		 * @param idPublico ID público de la cuenta
		 * @param saldo     Saldo inicial de la cuenta
		 */
		public PetCrear(String idPrivado, String idPublico, int saldo) {
			this.idPrivado = idPrivado;
			this.idPublico = idPublico;
			this.saldo = saldo;
			this.resp = Channel.one2one();
		}
	}

	/**
	 * Clase interna para manejar las peticiones de consulta de saldo.
	 */
	public class PetDisponible {
		String idPrivado;
		One2OneChannel resp;

		/**
		 * Constructor para la petición de consulta de saldo.
		 *
		 * @param idPrivado ID privado de la cuenta
		 */
		public PetDisponible(String idPrivado) {
			this.idPrivado = idPrivado;
			this.resp = Channel.one2one();
		}
	}

	/**
	 * Clase interna para manejar las peticiones de transferencia de fondos.
	 */
	public class PetTransferir {
		String idPrivado;
		String idPublicoDestino;
		int valor;
		boolean blocked;
		One2OneChannel resp;

		/**
		 * Constructor para la petición de transferencia de fondos.
		 *
		 * @param idPrivado        ID privado de la cuenta de origen
		 * @param idPublicoDestino ID público de la cuenta de destino
		 * @param valor            Monto a transferir
		 */
		public PetTransferir(String idPrivado, String idPublicoDestino, int valor) {
			this.idPrivado = idPrivado;
			this.idPublicoDestino = idPublicoDestino;
			this.valor = valor;
			this.blocked = false;
			this.resp = Channel.one2one();
		}
	}

	/**
	 * Clase interna para manejar las peticiones de alerta de saldo.
	 */
	public class PetAlertar {
		String idPrivado;
		int max;
		One2OneChannel resp;

		/**
		 * Constructor para la petición de alerta de saldo.
		 *
		 * @param idPrivado ID privado de la cuenta
		 * @param max       Saldo máximo para la alerta
		 */
		public PetAlertar(String idPrivado, int max) {
			this.idPrivado = idPrivado;
			this.max = max;
			this.resp = Channel.one2one();
		}
	}

	/**
	 * Constructor para la clase BlockchainCSP.
	 * Inicializa los canales y estructuras de datos, y comienza el proceso.
	 */
	public BlockchainCSP() {
		this.chCrear = Channel.any2one();
		this.chAlertar = Channel.any2one();
		this.chDisponible = Channel.any2one();
		this.chTransferir = Channel.any2one();
		this.cuentas = new HashTableMap<>();
		this.identidades = new HashTableMap<>();
		this.peticionesTransferir = new ArrayList<>();
		this.peticionesAlertar = new ArrayList<>();
		new ProcessManager(this).start();
	}

	/**
	 * Crea una nueva cuenta en la blockchain.
	 *
	 * @param idPrivado ID privado de la cuenta
	 * @param idPublico ID público de la cuenta
	 * @param saldo     Saldo inicial de la cuenta
	 * @throws IllegalArgumentException Si los parámetros son inválidos
	 */
	public void crear(String idPrivado, String idPublico, int saldo) {
		PetCrear peticion = new PetCrear(idPrivado, idPublico, saldo);
		chCrear.out().write(peticion);
		Boolean result = (Boolean) peticion.resp.in().read();
		if (!result || idPrivado == null || idPublico == null || saldo < 0) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Transfiere fondos entre cuentas en la blockchain.
	 *
	 * @param idPrivado        ID privado de la cuenta de origen
	 * @param idPublicoDestino ID público de la cuenta de destino
	 * @param valor            Monto a transferir
	 * @throws IllegalArgumentException Si los parámetros son inválidos o la
	 *                                  transferencia falla
	 */
	public void transferir(String idPrivado, String idPublicoDestino, int valor) {
		PetTransferir peticion = new PetTransferir(idPrivado, idPublicoDestino, valor);
		chTransferir.out().write(peticion);
		Boolean result = (Boolean) peticion.resp.in().read();
		if (!result) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Consulta el saldo de una cuenta en la blockchain.
	 *
	 * @param idPrivado ID privado de la cuenta
	 * @return El saldo disponible en la cuenta
	 * @throws IllegalArgumentException Si el ID privado es inválido o la cuenta no
	 *                                  existe
	 */
	public int disponible(String idPrivado) {
		if (idPrivado == null) {
			throw new IllegalArgumentException();
		}
		PetDisponible peticion = new PetDisponible(idPrivado);
		chDisponible.out().write(peticion);
		Integer result = (Integer) peticion.resp.in().read();
		if (result == null) {
			throw new IllegalArgumentException();
		} else {
			return result;
		}
	}

	/**
	 * Establece una alerta de saldo máximo para una cuenta en la blockchain.
	 *
	 * @param idPrivado ID privado de la cuenta
	 * @param max       Saldo máximo para la alerta
	 * @throws IllegalArgumentException Si los parámetros son inválidos
	 */
	public void alertarMax(String idPrivado, int max) {
		if (idPrivado == null || max < 0) {
			throw new IllegalArgumentException();
		}
		PetAlertar peticion = new PetAlertar(idPrivado, max);
		chAlertar.out().write(peticion);
		Boolean result = (Boolean) peticion.resp.in().read();
		if (!result) {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Método principal del proceso CSP. Gestiona las peticiones de creación,
	 * consulta de saldo, transferencias y alertas.
	 */
	public void run() {
		final int CREAR = 0;
		final int DISPONIBLE = 1;
		final int TRANSFERIR = 2;
		final int ALERTAR = 3;

		final Guard[] guards = new AltingChannelInput[4];
		guards[CREAR] = chCrear.in();
		guards[DISPONIBLE] = chDisponible.in();
		guards[TRANSFERIR] = chTransferir.in();
		guards[ALERTAR] = chAlertar.in();
		Alternative servicios = new Alternative(guards);

		while (true) {
			int servicio = servicios.fairSelect();

			switch (servicio) {
				case CREAR:
					PetCrear petCrear = (PetCrear) chCrear.in().read();
					if (petCrear.saldo < 0 || cuentas.containsKey(petCrear.idPrivado)
							|| identidades.containsKey(petCrear.idPublico)) {
						petCrear.resp.out().write(false);
					} else {
						cuentas.put(petCrear.idPrivado, petCrear.saldo);
						identidades.put(petCrear.idPublico, petCrear.idPrivado);
						petCrear.resp.out().write(true);
						desbloquearTransacciones();
					}
					break;

				case DISPONIBLE:
					PetDisponible petDisponible = (PetDisponible) chDisponible.in().read();
					if (!cuentas.containsKey(petDisponible.idPrivado)) {
						petDisponible.resp.out().write(null);
					} else {
						petDisponible.resp.out().write(cuentas.get(petDisponible.idPrivado));
					}
					break;

				case TRANSFERIR:
					PetTransferir petTransferir = (PetTransferir) chTransferir.in().read();
					if (petTransferir.valor <= 0 || !cuentas.containsKey(petTransferir.idPrivado)
							|| !identidades.containsKey(petTransferir.idPublicoDestino)
							|| petTransferir.idPrivado.equals(identidades.get(petTransferir.idPublicoDestino))) {
						petTransferir.resp.out().write(false);
					} else {
						if (hayTransaccionPendiente(petTransferir.idPrivado)
								|| cuentas.get(petTransferir.idPrivado) < petTransferir.valor) {
							petTransferir.blocked = true;
							peticionesTransferir.add(petTransferir);
						} else {
							cuentas.put(petTransferir.idPrivado,
									cuentas.get(petTransferir.idPrivado) - petTransferir.valor);
							cuentas.put(identidades.get(petTransferir.idPublicoDestino),
									cuentas.get(identidades.get(petTransferir.idPublicoDestino))
											+ petTransferir.valor);
							petTransferir.resp.out().write(true);
							desbloquearTransacciones();
						}
					}
					break;

				case ALERTAR:
					PetAlertar petAlertar = (PetAlertar) chAlertar.in().read();
					if (!cuentas.containsKey(petAlertar.idPrivado)) {
						petAlertar.resp.out().write(false);
					} else {
						if (cuentas.get(petAlertar.idPrivado) > petAlertar.max) {
							petAlertar.resp.out().write(true);
							desbloquearTransacciones();
						} else {
							peticionesAlertar.add(petAlertar);
						}
					}
					desbloquearTransacciones();
					break;
			}
		}
	}

	/**
	 * Desbloquea las transacciones pendientes si se cumplen las condiciones
	 * necesarias.
	 */
	private void desbloquearTransacciones() {
		boolean cambio = false;
		String solicitante = "";

		// Procesa las solicitudes de transferencia
		for (int i = 0; i < peticionesTransferir.size() && !cambio; i++) {
			PetTransferir peticion = peticionesTransferir.get(i);
			if (solicitante.equals(peticion.idPrivado)) {
				continue;
			}
			if (cuentas.get(peticion.idPrivado) >= peticion.valor) {
				cuentas.put(peticion.idPrivado, cuentas.get(peticion.idPrivado) - peticion.valor);
				cuentas.put(identidades.get(peticion.idPublicoDestino),
						cuentas.get(identidades.get(peticion.idPublicoDestino)) + peticion.valor);
				peticion.resp.out().write(true);
				peticionesTransferir.remove(i);
				cambio = true;
			}
			solicitante = peticion.idPrivado;
		}

		// Procesa las solicitudes de alerta
		for (int i = 0; i < peticionesAlertar.size() && !cambio; i++) {
			PetAlertar peticion = peticionesAlertar.get(i);
			if (cuentas.get(peticion.idPrivado) > peticion.max) {
				peticion.resp.out().write(true);
				peticionesAlertar.remove(i);
				cambio = true;
			}
		}

		// Asegura desbloquear solicitudes adicionales si hubo cambios
		if (cambio) {
			desbloquearTransacciones();
		}
	}

	/**
	 * Verifica si hay transacciones pendientes para una cuenta específica.
	 *
	 * @param idPrivado ID privado de la cuenta
	 * @return true si hay transacciones pendientes, false en caso contrario
	 */
	private boolean hayTransaccionPendiente(String idPrivado) {
		for (PetTransferir peticion : peticionesTransferir) {
			if (peticion.idPrivado.equals(idPrivado)) {
				return true;
			}
		}
		return false;
	}
}