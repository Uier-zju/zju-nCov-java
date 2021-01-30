package model;

import lombok.Data;

@Data
public class LoginParam {

	private String username;

	private String password;

	private String execution;

	private String _eventId;

}